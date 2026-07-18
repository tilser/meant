package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddress;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddressSelection;
import com.meant.api.plugin.cart.common.dto.CartDeliveryOptionSelection;
import com.meant.api.plugin.cart.common.dto.CartToolArguments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** Merges partial delivery changes into complete ordered UCP fulfillment state. */
@Service
public class CartFulfillmentReplacementService {

    public CartToolArguments.Fulfillment merge(
            CartToolArguments.Fulfillment current,
            List<CartDeliveryAddressSelection> addressesToAdd,
            List<CartDeliveryAddressSelection> addressesToReplace,
            List<CartDeliveryOptionSelection> selectedOptions
    ) {
        if (addressesToAdd == null && addressesToReplace == null && selectedOptions == null) {
            return current;
        }
        List<CartToolArguments.FulfillmentMethod> methods = current == null
                ? List.of() : safeList(current.methods());
        if (methods.isEmpty()) {
            return CartToolArguments.fulfillment(addressesToAdd, addressesToReplace, selectedOptions);
        }
        if (methods.stream().anyMatch(Objects::isNull)) {
            throw CartException.rejected("Existing fulfillment methods cannot contain null entries");
        }

        List<CartToolArguments.FulfillmentMethod> merged = new ArrayList<>(methods);
        if (addressesToReplace != null) {
            applyAddresses(merged, addressesToReplace, true);
        }
        if (addressesToAdd != null && !addressesToAdd.isEmpty()) {
            applyAddresses(merged, addressesToAdd, false);
        }
        if (selectedOptions != null) {
            applyOptions(merged, selectedOptions);
        }
        return new CartToolArguments.Fulfillment(List.copyOf(merged), current.extensions());
    }

    private void applyAddresses(
            List<CartToolArguments.FulfillmentMethod> methods,
            List<CartDeliveryAddressSelection> requests,
            boolean replace
    ) {
        Map<Integer, List<CartDeliveryAddressSelection>> targets = addressTargets(methods, requests);
        for (Map.Entry<Integer, List<CartDeliveryAddressSelection>> entry : targets.entrySet()) {
            CartToolArguments.FulfillmentMethod method = methods.get(entry.getKey());
            boolean clear = entry.getValue().isEmpty() || entry.getValue().stream().anyMatch(this::addressClear);
            List<CartDeliveryAddress> requested = entry.getValue().stream()
                    .filter(request -> !addressClear(request))
                    .map(CartDeliveryAddressSelection::address)
                    .toList();
            if (clear && !requested.isEmpty()) {
                throw CartException.rejected("A fulfillment update cannot clear and replace the same method state");
            }
            List<CartDeliveryAddress> destinations = clear ? List.of()
                    : replace ? replacementAddresses(method.destinations(), requested)
                    : mergeAddresses(method.destinations(), requested);
            String requestedSelection = entry.getValue().stream()
                    .filter(request -> Boolean.TRUE.equals(request.selected()))
                    .map(CartDeliveryAddressSelection::address)
                    .filter(Objects::nonNull)
                    .map(CartDeliveryAddress::id)
                    .filter(this::hasText)
                    .findFirst()
                    .orElse(null);
            String selectedDestinationId = requestedSelection != null
                    ? requestedSelection
                    : retainedSelection(method.selectedDestinationId(), destinations);
            methods.set(entry.getKey(), new CartToolArguments.FulfillmentMethod(
                    method.id(), method.type(), method.lineItemIds(), destinations,
                    selectedDestinationId, method.groups(), method.extensions()));
        }
    }

    private void applyOptions(
            List<CartToolArguments.FulfillmentMethod> methods,
            List<CartDeliveryOptionSelection> requests
    ) {
        Map<Integer, List<CartDeliveryOptionSelection>> targets = optionTargets(methods, requests);
        for (Map.Entry<Integer, List<CartDeliveryOptionSelection>> entry : targets.entrySet()) {
            CartToolArguments.FulfillmentMethod method = methods.get(entry.getKey());
            boolean clear = entry.getValue().isEmpty() || entry.getValue().stream().anyMatch(this::optionClear);
            if (clear && entry.getValue().stream().anyMatch(request -> !optionClear(request))) {
                throw CartException.rejected("A fulfillment update cannot clear and replace the same method state");
            }
            List<CartToolArguments.FulfillmentGroup> groups = clear
                    ? List.of() : mergeGroups(method.groups(), entry.getValue());
            methods.set(entry.getKey(), new CartToolArguments.FulfillmentMethod(
                    method.id(), method.type(), method.lineItemIds(), method.destinations(),
                    method.selectedDestinationId(), groups, method.extensions()));
        }
    }

    private Map<Integer, List<CartDeliveryAddressSelection>> addressTargets(
            List<CartToolArguments.FulfillmentMethod> methods,
            List<CartDeliveryAddressSelection> requests
    ) {
        if (requests.isEmpty()) {
            return Map.of(singleMethod(methods), List.of());
        }
        Map<Integer, List<CartDeliveryAddressSelection>> targets = new LinkedHashMap<>();
        for (CartDeliveryAddressSelection request : requests) {
            if (request == null) {
                throw CartException.rejected("Fulfillment update entries cannot be null");
            }
            int target = resolveAddressMethod(methods, request);
            targets.computeIfAbsent(target, ignored -> new ArrayList<>()).add(request);
        }
        return targets;
    }

    private Map<Integer, List<CartDeliveryOptionSelection>> optionTargets(
            List<CartToolArguments.FulfillmentMethod> methods,
            List<CartDeliveryOptionSelection> requests
    ) {
        if (requests.isEmpty()) {
            return Map.of(singleMethod(methods), List.of());
        }
        Map<Integer, List<CartDeliveryOptionSelection>> targets = new LinkedHashMap<>();
        for (CartDeliveryOptionSelection request : requests) {
            if (request == null) {
                throw CartException.rejected("Fulfillment update entries cannot be null");
            }
            int target = resolveOptionMethod(methods, request);
            targets.computeIfAbsent(target, ignored -> new ArrayList<>()).add(request);
        }
        return targets;
    }

    private int resolveAddressMethod(
            List<CartToolArguments.FulfillmentMethod> methods,
            CartDeliveryAddressSelection request
    ) {
        if (methods.size() == 1) {
            return 0;
        }
        List<Integer> matches = indexes(methods, request.methodId(),
                request.address() == null ? null : request.address().id(), true);
        return exactlyOne(matches);
    }

    private int resolveOptionMethod(
            List<CartToolArguments.FulfillmentMethod> methods,
            CartDeliveryOptionSelection request
    ) {
        if (methods.size() == 1) {
            return 0;
        }
        List<Integer> matches = indexes(methods, request.methodId(), request.groupId(), false);
        return exactlyOne(matches);
    }

    private List<Integer> indexes(
            List<CartToolArguments.FulfillmentMethod> methods,
            String explicitMethodId,
            String memberId,
            boolean destinations
    ) {
        List<Integer> matches = new ArrayList<>();
        for (int index = 0; index < methods.size(); index++) {
            CartToolArguments.FulfillmentMethod method = methods.get(index);
            if (hasText(explicitMethodId)) {
                if (explicitMethodId.trim().equals(method.id())) {
                    matches.add(index);
                }
            } else if (hasText(memberId) && (destinations
                    ? method.destinations().stream().anyMatch(value -> memberId.trim().equals(value.id()))
                    : method.groups().stream().anyMatch(value -> memberId.trim().equals(value.id())))) {
                matches.add(index);
            }
        }
        return matches;
    }

    private int exactlyOne(List<Integer> matches) {
        if (matches.size() != 1) {
            throw ambiguous();
        }
        return matches.getFirst();
    }

    private int singleMethod(List<CartToolArguments.FulfillmentMethod> methods) {
        if (methods.size() != 1) {
            throw ambiguous();
        }
        return 0;
    }

    private List<CartDeliveryAddress> mergeAddresses(
            List<CartDeliveryAddress> existing,
            List<CartDeliveryAddress> requested
    ) {
        List<CartDeliveryAddress> merged = new ArrayList<>(safeList(existing));
        List<CartDeliveryAddress> additions = new ArrayList<>();
        for (CartDeliveryAddress address : requested) {
            int index = addressIndex(merged, address.id());
            if (index < 0) {
                additions.add(address);
            } else {
                merged.set(index, preserveAddressExtensions(merged.get(index), address));
            }
        }
        merged.addAll(sortedAddresses(additions));
        return List.copyOf(merged);
    }

    private List<CartDeliveryAddress> replacementAddresses(
            List<CartDeliveryAddress> existing,
            List<CartDeliveryAddress> requested
    ) {
        return sortedAddresses(requested.stream()
                .map(address -> {
                    int index = addressIndex(safeList(existing), address.id());
                    return index < 0 ? address : preserveAddressExtensions(existing.get(index), address);
                })
                .toList());
    }

    private CartDeliveryAddress preserveAddressExtensions(
            CartDeliveryAddress existing,
            CartDeliveryAddress requested
    ) {
        Map<String, JsonNode> extensions = new LinkedHashMap<>(existing.extensions());
        extensions.putAll(requested.extensions());
        return new CartDeliveryAddress(
                requested.id(), requested.firstName(), requested.lastName(), requested.phoneNumber(),
                requested.streetAddress(), requested.extendedAddress(), requested.addressLocality(),
                requested.addressRegion(), requested.postalCode(), requested.addressCountry(), extensions);
    }

    private String retainedSelection(String selectedDestinationId, List<CartDeliveryAddress> destinations) {
        if (!hasText(selectedDestinationId)) {
            return null;
        }
        String normalizedId = selectedDestinationId.trim();
        return destinations.stream().anyMatch(address -> normalizedId.equals(address.id()))
                ? selectedDestinationId
                : null;
    }

    private List<CartToolArguments.FulfillmentGroup> mergeGroups(
            List<CartToolArguments.FulfillmentGroup> existing,
            List<CartDeliveryOptionSelection> requested
    ) {
        List<CartToolArguments.FulfillmentGroup> merged = new ArrayList<>(safeList(existing));
        for (CartDeliveryOptionSelection selection : requested) {
            if (!hasText(selection.groupId()) || !hasText(selection.selectedOptionId())) {
                throw CartException.rejected("Fulfillment update identity is incomplete");
            }
            int index = groupIndex(merged, selection.groupId());
            if (index < 0) {
                merged.add(new CartToolArguments.FulfillmentGroup(
                        selection.groupId().trim(), List.of(), List.of(), selection.selectedOptionId().trim()));
            } else {
                CartToolArguments.FulfillmentGroup group = merged.get(index);
                merged.set(index, new CartToolArguments.FulfillmentGroup(
                        group.id(), group.lineItemIds(), group.options(), selection.selectedOptionId().trim(),
                        group.extensions()));
            }
        }
        return merged.stream()
                .sorted(Comparator.comparing(group -> Objects.toString(group.id(), "")))
                .toList();
    }

    private int addressIndex(List<CartDeliveryAddress> values, String id) {
        if (!hasText(id)) {
            return -1;
        }
        for (int index = 0; index < values.size(); index++) {
            if (id.trim().equals(values.get(index).id())) {
                return index;
            }
        }
        return -1;
    }

    private int groupIndex(List<CartToolArguments.FulfillmentGroup> values, String id) {
        for (int index = 0; index < values.size(); index++) {
            if (id.trim().equals(values.get(index).id())) {
                return index;
            }
        }
        return -1;
    }

    private List<CartDeliveryAddress> sortedAddresses(List<CartDeliveryAddress> values) {
        return values.stream().sorted(Comparator.comparing(value -> Objects.toString(value.id(), ""))).toList();
    }

    private boolean addressClear(CartDeliveryAddressSelection request) {
        return request.address() == null || request.address().empty();
    }

    private boolean optionClear(CartDeliveryOptionSelection request) {
        return !hasText(request.groupId()) && !hasText(request.selectedOptionId());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private CartException ambiguous() {
        return CartException.binding(CartException.BindingFailure.IDENTITY_MISMATCH,
                "Fulfillment update does not identify exactly one existing method");
    }
}
