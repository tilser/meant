package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.plugin.cart.common.dto.CartToolArguments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/** Merges partial delivery changes into the complete ordered UCP fulfillment method state. */
@Service
public class CartFulfillmentReplacementService {

    public CartToolArguments.Fulfillment merge(
            CartToolArguments.Fulfillment current,
            List<Map<String, Object>> addressesToAdd,
            List<Map<String, Object>> addressesToReplace,
            List<Map<String, Object>> selectedOptions
    ) {
        if (addressesToAdd == null && addressesToReplace == null && selectedOptions == null) {
            return current;
        }
        List<Map<String, Object>> methods = current == null ? List.of() : safeList(current.methods());
        if (methods.isEmpty()) {
            return CartToolArguments.fulfillment(addressesToAdd, addressesToReplace, selectedOptions);
        }
        if (methods.stream().anyMatch(Objects::isNull)) {
            throw CartException.rejected("Existing fulfillment methods cannot contain null entries");
        }

        List<Map<String, Object>> merged = new ArrayList<>(methods);
        if (addressesToReplace != null) {
            apply(merged, addressesToReplace, "destinations", UpdateKind.REPLACE_DESTINATIONS);
        }
        if (addressesToAdd != null && !addressesToAdd.isEmpty()) {
            apply(merged, addressesToAdd, "destinations", UpdateKind.ADD_DESTINATIONS);
        }
        if (selectedOptions != null) {
            apply(merged, selectedOptions, "groups", UpdateKind.MERGE_GROUPS);
        }
        return new CartToolArguments.Fulfillment(List.copyOf(merged));
    }

    private void apply(
            List<Map<String, Object>> methods,
            List<Map<String, Object>> requests,
            String collectionKey,
            UpdateKind kind
    ) {
        Map<Integer, List<Map<String, Object>>> byMethod = targets(methods, requests, collectionKey);
        for (Map.Entry<Integer, List<Map<String, Object>>> entry : byMethod.entrySet()) {
            int index = entry.getKey();
            Map<String, Object> method = new LinkedHashMap<>(methods.get(index));
            List<Map<String, Object>> requested = mapped(entry.getValue(), kind);
            boolean clear = entry.getValue().isEmpty() || entry.getValue().stream().anyMatch(this::clearMarker);
            boolean malformed = entry.getValue().stream()
                    .anyMatch(request -> !clearMarker(request) && map(request, collectionKey).isEmpty());
            if (malformed || (kind == UpdateKind.MERGE_GROUPS && requested.stream()
                    .anyMatch(group -> text(group.get("id")).isEmpty()))) {
                throw CartException.rejected("Fulfillment update identity is incomplete");
            }
            if (clear && !requested.isEmpty()) {
                throw CartException.rejected("A fulfillment update cannot clear and replace the same method state");
            }
            List<Map<String, Object>> existing = maps(method.get(collectionKey));
            List<Map<String, Object>> replacement = switch (kind) {
                case REPLACE_DESTINATIONS -> clear ? List.of() : sorted(requested);
                case ADD_DESTINATIONS -> mergeByIdentity(existing, requested, false);
                case MERGE_GROUPS -> clear ? List.of() : mergeByIdentity(existing, requested, true);
            };
            method.put(collectionKey, replacement);
            methods.set(index, method);
        }
    }

    private Map<Integer, List<Map<String, Object>>> targets(
            List<Map<String, Object>> methods,
            List<Map<String, Object>> requests,
            String collectionKey
    ) {
        if (requests.isEmpty()) {
            if (methods.size() != 1) {
                throw ambiguous();
            }
            return Map.of(0, List.of());
        }
        Map<Integer, List<Map<String, Object>>> targets = new LinkedHashMap<>();
        for (Map<String, Object> request : requests) {
            if (request == null) {
                throw CartException.rejected("Fulfillment update entries cannot be null");
            }
            int index = resolveMethod(methods, request, collectionKey);
            targets.computeIfAbsent(index, ignored -> new ArrayList<>()).add(request);
        }
        return targets;
    }

    private int resolveMethod(
            List<Map<String, Object>> methods,
            Map<String, Object> request,
            String collectionKey
    ) {
        if (methods.size() == 1) {
            return 0;
        }
        String explicitMethodId = text(first(request, "method_id", "fulfillment_method_id", "methodId"));
        List<Integer> matches = new ArrayList<>();
        for (int index = 0; index < methods.size(); index++) {
            Map<String, Object> method = methods.get(index);
            if (!explicitMethodId.isEmpty()) {
                if (explicitMethodId.equals(text(first(method, "id", "method_id", "methodId")))) {
                    matches.add(index);
                }
                continue;
            }
            Map<String, Object> mapped = map(request, collectionKey);
            String itemId = text(mapped.get("id"));
            if (!itemId.isEmpty() && maps(method.get(collectionKey)).stream()
                    .anyMatch(item -> itemId.equals(text(item.get("id"))))) {
                matches.add(index);
            }
        }
        if (matches.size() != 1) {
            throw ambiguous();
        }
        return matches.getFirst();
    }

    private List<Map<String, Object>> mapped(List<Map<String, Object>> requests, UpdateKind kind) {
        String collectionKey = kind == UpdateKind.MERGE_GROUPS ? "groups" : "destinations";
        return requests.stream().map(request -> map(request, collectionKey))
                .filter(value -> !value.isEmpty()).toList();
    }

    private Map<String, Object> map(Map<String, Object> request, String collectionKey) {
        return "groups".equals(collectionKey)
                ? CartToolArguments.fulfillmentGroup(request)
                : CartToolArguments.destination(request);
    }

    private List<Map<String, Object>> mergeByIdentity(
            List<Map<String, Object>> existing,
            List<Map<String, Object>> requested,
            boolean mergeFields
    ) {
        List<Map<String, Object>> merged = new ArrayList<>(existing);
        List<Map<String, Object>> additions = new ArrayList<>();
        for (Map<String, Object> item : requested) {
            String id = text(item.get("id"));
            int index = indexOf(merged, id);
            if (index < 0) {
                additions.add(item);
            } else if (mergeFields) {
                Map<String, Object> value = new LinkedHashMap<>(merged.get(index));
                value.putAll(item);
                merged.set(index, value);
            } else {
                merged.set(index, item);
            }
        }
        merged.addAll(sorted(additions));
        return List.copyOf(merged);
    }

    private int indexOf(List<Map<String, Object>> values, String id) {
        if (id.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < values.size(); index++) {
            if (id.equals(text(values.get(index).get("id")))) {
                return index;
            }
        }
        return -1;
    }

    private List<Map<String, Object>> sorted(List<Map<String, Object>> values) {
        return values.stream().sorted(Comparator.comparing(this::stableKey)).toList();
    }

    private String stableKey(Map<String, Object> value) {
        return text(value.get("id")) + '\u0000' + new TreeMap<>(value);
    }

    private boolean clearMarker(Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return true;
        }
        return request.keySet().stream().allMatch(key ->
                key.equals("method_id") || key.equals("fulfillment_method_id") || key.equals("methodId"));
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> values = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw CartException.rejected("Existing fulfillment state is malformed");
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> copy.put(String.valueOf(key), entry));
            values.add(copy);
        }
        return List.copyOf(values);
    }

    private Object first(Map<String, Object> values, String... keys) {
        if (values == null) {
            return null;
        }
        for (String key : keys) {
            if (values.get(key) != null) {
                return values.get(key);
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private CartException ambiguous() {
        return CartException.binding(CartException.BindingFailure.IDENTITY_MISMATCH,
                "Fulfillment update does not identify exactly one existing method");
    }

    private enum UpdateKind {
        REPLACE_DESTINATIONS,
        ADD_DESTINATIONS,
        MERGE_GROUPS
    }
}
