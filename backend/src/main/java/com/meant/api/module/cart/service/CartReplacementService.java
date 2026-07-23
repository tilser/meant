package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeList;
import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.command.UpdateCartCommand;
import com.meant.api.module.cart.service.dto.CartBuyerIdentityInput;
import com.meant.api.module.cart.service.dto.CartDeliveryAddressInput;
import com.meant.api.module.cart.service.dto.CartDeliveryAddressSelectionInput;
import com.meant.api.module.cart.service.dto.CartDeliveryOptionSelectionInput;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartBuyer;
import com.meant.api.plugin.cart.common.dto.CartContext;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddress;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddressSelection;
import com.meant.api.plugin.cart.common.dto.CartDeliveryOptionSelection;
import com.meant.api.plugin.cart.common.dto.CartToolArguments;
import com.meant.api.plugin.cart.common.dto.CartUpdateItem;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.update.dto.CartReplacementState;
import com.meant.api.plugin.cart.update.dto.UpdateCartRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Builds and proves complete PUT-style cart state without retaining remote buyer payloads. */
@Service
@RequiredArgsConstructor
public class CartReplacementService {
    private final CartLineOfferIdentityMapper identityMapper;
    private final CartFulfillmentReplacementService fulfillmentReplacementService;

    public void validateIdentifiers(Cart cart, UpdateCartCommand command) {
        Map<UUID, CartLine> local = localLines(cart);
        Map<String, CartLine> remote = remoteLines(cart);
        for (UpdateCartCommand.UpdateItem item : safeList(command.updateItems())) {
            boolean hasLocal = item.cartLineId() != null;
            boolean hasRemote = hasText(item.remoteCartLineId());
            if (!hasLocal && !hasRemote) {
                throw CartException.rejected("A cart update item requires a local or remote line id");
            }
            CartLine byLocal = hasLocal ? local.get(item.cartLineId()) : null;
            CartLine byRemote = hasRemote ? remote.get(item.remoteCartLineId().trim()) : null;
            if (hasLocal && byLocal == null || hasRemote && byRemote == null) {
                throw CartException.notFound("Cart line not found");
            }
            if (hasLocal && hasRemote && byLocal != byRemote) {
                throw CartException.rejected("Local and remote cart line ids identify different lines");
            }
        }
    }

    public UpdateCartRequest build(
            Cart cart,
            UpdateCartCommand command,
            List<CartAddItem> addedItems,
            CartContext derivedContext,
            UcpCartResponse currentRemote
    ) {
        Map<UUID, CartLine> local = localLines(cart);
        Map<String, CartLine> remote = remoteLines(cart);
        List<CartUpdateItem> removals = removeItems(command, local, remote);
        List<CartUpdateItem> updates = safeList(command.updateItems()).stream()
                .map(item -> updateItem(item, local, remote))
                .toList();
        CartContext requestContext = context(derivedContext, command.buyerIdentity());
        CartReplacementState replacement = providerBound(cart)
                ? replacementState(cart, command, addedItems, requestContext, currentRemote)
                : null;
        return new UpdateCartRequest(
                cart.getRemoteCartId(), addedItems, updates,
                removals.stream().map(CartUpdateItem::id).toList(), removals,
                buyer(command.buyerIdentity()), requestContext,
                addresses(command.deliveryAddressesToAdd()), addresses(command.deliveryAddressesToReplace()),
                options(command.selectedDeliveryOptions()), normalizeCodes(command.discountCodes()),
                normalizeCodes(command.giftCardCodes()), command.note(), replacement);
    }

    public boolean proves(UcpCartResponse response, CartReplacementState intended) {
        if (response == null || response.cart() == null || intended == null) {
            return false;
        }
        List<LineIdentity> expected = intended.lineItems().stream().map(this::identity).toList();
        List<LineIdentity> actual = new ArrayList<>();
        for (UcpCartResponse.Line line : safeNonNullList(response.cart().lines())) {
            UcpCartResponse.Merchandise merchandise = line.merchandise();
            if (merchandise == null || !hasText(merchandise.id()) || line.quantity() == null) {
                return false;
            }
            actual.add(identity(new CartAddItem(
                    merchandise.resolvedProductId(), merchandise.id(), merchandise.selectedOptions(),
                    merchandise.components(), merchandise.sellingPlan(), line.quantity())));
        }
        return matchesUnambiguously(expected, actual);
    }

    private boolean matchesUnambiguously(List<LineIdentity> expected, List<LineIdentity> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        List<LineIdentity> unmatched = new ArrayList<>(expected);
        List<LineIdentity> sparse = new ArrayList<>();
        for (LineIdentity observed : actual) {
            int exact = unmatched.indexOf(observed);
            if (exact >= 0) {
                unmatched.remove(exact);
            } else {
                sparse.add(observed);
            }
        }
        for (LineIdentity observed : sparse) {
            List<LineIdentity> candidates = unmatched.stream()
                    .filter(candidate -> compatible(candidate, observed))
                    .distinct()
                    .toList();
            if (candidates.size() != 1) {
                return false;
            }
            unmatched.remove(candidates.getFirst());
        }
        return unmatched.isEmpty();
    }

    private boolean compatible(LineIdentity expected, LineIdentity observed) {
        return expected.variantId().equals(observed.variantId())
                && expected.quantity().equals(observed.quantity())
                && (!hasText(observed.productId()) || expected.productId().equals(observed.productId()))
                && (observed.options().isEmpty() || expected.options().equals(observed.options()))
                && (observed.components().isEmpty() || expected.components().equals(observed.components()))
                && (observed.sellingPlan().isEmpty() || expected.sellingPlan().equals(observed.sellingPlan()));
    }

    private CartReplacementState replacementState(
            Cart cart,
            UpdateCartCommand command,
            List<CartAddItem> addedItems,
            CartContext derivedContext,
            UcpCartResponse response
    ) {
        UcpCartResponse.Cart remote = response == null ? null : response.cart();
        if (remote == null) {
            throw CartException.binding(CartException.BindingFailure.PROVIDER_FAILURE,
                    "A fresh remote cart snapshot is required before replacement");
        }
        List<CartAddItem> lines = replacementLines(cart, command, addedItems, remote);
        CartContext context = remote.context() == null
                ? derivedContext
                : remote.context().merge(derivedContext);
        List<String> discounts = command.discountCodes() == null
                ? remoteDiscountCodes(remote) : normalizeCodes(command.discountCodes());
        List<String> giftCards = command.giftCardCodes() == null
                ? remoteGiftCardCodes(remote) : normalizeCodes(command.giftCardCodes());
        return new CartReplacementState(
                lines,
                replacementBuyer(remote.buyer(), command.buyerIdentity()),
                context,
                remote.signals(),
                fulfillmentReplacementService.merge(
                        remote.fulfillment(), addresses(command.deliveryAddressesToAdd()),
                        addresses(command.deliveryAddressesToReplace()), options(command.selectedDeliveryOptions())),
                new CartToolArguments.Discounts(discounts),
                giftCards,
                command.note() == null ? remote.note() : command.note());
    }

    private CartBuyer buyer(CartBuyerIdentityInput input) {
        return input == null ? null : new CartBuyer(
                text(input.firstName()), text(input.lastName()), text(input.email()), text(input.phoneNumber()));
    }

    private CartBuyer replacementBuyer(CartBuyer remote, CartBuyerIdentityInput input) {
        if (input == null) {
            return remote;
        }
        CartBuyer requested = buyer(input);
        return remote == null ? requested : remote.merge(requested);
    }

    private CartContext context(CartContext derived, CartBuyerIdentityInput buyer) {
        String country = buyer == null ? null : text(buyer.countryCode());
        if (country == null) {
            return derived;
        }
        CartContext buyerContext = new CartContext(country.toUpperCase(Locale.ROOT));
        return derived == null ? buyerContext : derived.merge(buyerContext);
    }

    private List<CartDeliveryAddressSelection> addresses(List<CartDeliveryAddressSelectionInput> inputs) {
        return inputs == null ? null : safeNonNullList(inputs).stream().map(input -> new CartDeliveryAddressSelection(
                text(input.methodId()), input.selected(), address(input))).toList();
    }

    private CartDeliveryAddress address(CartDeliveryAddressSelectionInput input) {
        CartDeliveryAddressInput value = input.address();
        return new CartDeliveryAddress(
                text(input.id()), value == null ? null : text(value.firstName()),
                value == null ? null : text(value.lastName()), value == null ? null : text(value.phoneNumber()),
                value == null ? null : text(value.streetAddress()),
                value == null ? null : text(value.extendedAddress()),
                value == null ? null : text(value.addressLocality()),
                value == null ? null : text(value.addressRegion()), value == null ? null : text(value.postalCode()),
                value == null ? null : text(value.addressCountry()));
    }

    private List<CartDeliveryOptionSelection> options(List<CartDeliveryOptionSelectionInput> inputs) {
        return inputs == null ? null : safeNonNullList(inputs).stream().map(input -> new CartDeliveryOptionSelection(
                text(input.methodId()), text(input.groupId()), text(input.selectedOptionId()))).toList();
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<CartAddItem> replacementLines(
            Cart cart, UpdateCartCommand command, List<CartAddItem> additions, UcpCartResponse.Cart remoteCart) {
        Map<String, CartLine> localByRemote = remoteLines(cart);
        List<UcpCartResponse.Line> remoteLines = safeNonNullList(remoteCart.lines());
        if (remoteLines.size() != localByRemote.size()) {
            throw ambiguousSnapshot();
        }
        Map<String, CartLine> matchedRemoteLines = matchRemoteLines(cart, remoteLines);
        Map<CartLine, Integer> quantities = new HashMap<>();
        for (UpdateCartCommand.UpdateItem update : safeList(command.updateItems())) {
            quantities.put(resolve(update, localLines(cart), localByRemote), update.quantity());
        }
        Set<UUID> removedLocal = new HashSet<>(safeList(command.removeCartLineIds()));
        Set<String> removedRemote = safeList(command.removeRemoteCartLineIds()).stream()
                .filter(CartReplacementService::hasText).map(String::trim).collect(Collectors.toSet());
        List<CartAddItem> replacement = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (UcpCartResponse.Line remoteLine : remoteLines) {
            CartLine localLine = remoteLine == null ? null : matchedRemoteLines.get(remoteLine.id());
            if (localLine == null || remoteLine.quantity() == null
                    || remoteLine.merchandise() == null
                    || !seen.add(remoteLine.id())) {
                throw ambiguousSnapshot();
            }
            if (removedLocal.contains(localLine.getId())
                    || removedRemote.contains(localLine.getRemoteCartLineId())
                    || removedRemote.contains(remoteLine.id())) {
                continue;
            }
            int quantity = quantities.getOrDefault(localLine, remoteLine.quantity());
            if (quantity > 0) {
                replacement.add(identityMapper.toItem(localLine, quantity));
            }
        }
        if (seen.size() != localByRemote.size()) {
            throw ambiguousSnapshot();
        }
        replacement.addAll(safeList(additions));
        return List.copyOf(replacement);
    }

    private Map<String, CartLine> matchRemoteLines(Cart cart, List<UcpCartResponse.Line> remoteLines) {
        List<CartLine> unmatched = new ArrayList<>(cart.getLines());
        Map<String, CartLine> matches = new LinkedHashMap<>();
        for (UcpCartResponse.Line remoteLine : remoteLines) {
            if (remoteLine == null || !hasText(remoteLine.id()) || matches.containsKey(remoteLine.id())) {
                throw ambiguousSnapshot();
            }
            CartLine sameRemoteId = unmatched.stream()
                    .filter(line -> remoteLine.id().equals(line.getRemoteCartLineId()))
                    .findFirst()
                    .orElse(null);
            if (sameRemoteId != null) {
                if (!remoteIdentityCompatible(sameRemoteId, remoteLine)) {
                    throw ambiguousSnapshot();
                }
                unmatched.remove(sameRemoteId);
                matches.put(remoteLine.id(), sameRemoteId);
                continue;
            }
            List<CartLine> exact = unmatched.stream()
                    .filter(line -> remoteIdentityMatches(line, remoteLine))
                    .toList();
            List<CartLine> candidates = exact.isEmpty()
                    ? unmatched.stream().filter(line -> remoteIdentityCompatible(line, remoteLine)).toList()
                    : exact;
            if (candidates.size() != 1) {
                throw ambiguousSnapshot();
            }
            CartLine matched = candidates.getFirst();
            unmatched.remove(matched);
            matches.put(remoteLine.id(), matched);
        }
        if (!unmatched.isEmpty()) {
            throw ambiguousSnapshot();
        }
        return Map.copyOf(matches);
    }

    private boolean remoteIdentityMatches(CartLine local, UcpCartResponse.Line remote) {
        CartAddItem intended = identityMapper.toItem(local, remote.quantity());
        UcpCartResponse.Merchandise merchandise = remote.merchandise();
        CartAddItem observed = new CartAddItem(
                merchandise.resolvedProductId(), merchandise.id(), merchandise.selectedOptions(),
                merchandise.components(), merchandise.sellingPlan(), remote.quantity());
        LineIdentity expected = identity(intended);
        LineIdentity actual = identity(observed);
        boolean productMatches = !hasText(actual.productId()) || expected.productId().equals(actual.productId());
        return productMatches
                && expected.variantId().equals(actual.variantId())
                && expected.options().equals(actual.options())
                && expected.components().equals(actual.components())
                && expected.sellingPlan().equals(actual.sellingPlan());
    }

    private boolean remoteIdentityCompatible(CartLine local, UcpCartResponse.Line remote) {
        if (remote == null || remote.quantity() == null || remote.merchandise() == null) {
            return false;
        }
        LineIdentity expected = identity(identityMapper.toItem(local, remote.quantity()));
        UcpCartResponse.Merchandise merchandise = remote.merchandise();
        LineIdentity actual = identity(new CartAddItem(
                merchandise.resolvedProductId(), merchandise.id(), merchandise.selectedOptions(),
                merchandise.components(), merchandise.sellingPlan(), remote.quantity()));
        return expected.variantId().equals(actual.variantId())
                && (!hasText(actual.productId()) || expected.productId().equals(actual.productId()))
                && (actual.options().isEmpty() || expected.options().equals(actual.options()))
                && (actual.components().isEmpty() || expected.components().equals(actual.components()))
                && (actual.sellingPlan().isEmpty() || expected.sellingPlan().equals(actual.sellingPlan()));
    }

    private List<String> remoteDiscountCodes(UcpCartResponse.Cart cart) {
        if (cart.discounts() != null && cart.discounts().codes() != null) {
            return normalizeCodes(cart.discounts().codes());
        }
        return codes(cart.discountCodes());
    }

    private List<String> remoteGiftCardCodes(UcpCartResponse.Cart cart) {
        List<String> codes = codes(cart.giftCardCodes());
        return codes.isEmpty() ? codes(cart.appliedGiftCards()) : codes;
    }

    private List<String> codes(List<UcpCartResponse.AppliedCode> values) {
        return safeNonNullList(values).stream().map(UcpCartResponse.AppliedCode::code)
                .filter(CartReplacementService::hasText).map(String::trim).distinct().toList();
    }

    private List<CartUpdateItem> removeItems(
            UpdateCartCommand command, Map<UUID, CartLine> local, Map<String, CartLine> remote) {
        Map<String, CartUpdateItem> items = new LinkedHashMap<>();
        boolean hasResolvedRemoteRemoval = safeList(command.removeRemoteCartLineIds()).stream()
                .filter(CartReplacementService::hasText).map(String::trim).anyMatch(remote::containsKey);
        for (UUID id : safeList(command.removeCartLineIds())) {
            CartLine line = local.get(id);
            if (line == null) {
                if (hasResolvedRemoteRemoval) {
                    continue;
                }
                throw CartException.notFound("Cart line not found: " + id);
            }
            items.put(line.getRemoteCartLineId(), new CartUpdateItem(
                    line.getRemoteCartLineId(), line.getProductVariantId(), 0));
        }
        for (String id : safeList(command.removeRemoteCartLineIds())) {
            if (!hasText(id)) {
                continue;
            }
            CartLine line = remote.get(id.trim());
            if (line == null) {
                throw CartException.notFound("Cart line not found: " + id);
            }
            items.putIfAbsent(line.getRemoteCartLineId(), new CartUpdateItem(
                    line.getRemoteCartLineId(), line.getProductVariantId(), 0));
        }
        return List.copyOf(items.values());
    }

    private CartUpdateItem updateItem(
            UpdateCartCommand.UpdateItem item, Map<UUID, CartLine> local, Map<String, CartLine> remote) {
        CartLine line = resolve(item, local, remote);
        return new CartUpdateItem(line.getRemoteCartLineId(), line.getProductVariantId(), item.quantity());
    }

    private CartLine resolve(
            UpdateCartCommand.UpdateItem item, Map<UUID, CartLine> local, Map<String, CartLine> remote) {
        return item.cartLineId() != null ? local.get(item.cartLineId()) : remote.get(item.remoteCartLineId().trim());
    }

    private Map<UUID, CartLine> localLines(Cart cart) {
        return cart.getLines().stream().collect(Collectors.toMap(CartLine::getId, line -> line));
    }

    private Map<String, CartLine> remoteLines(Cart cart) {
        return cart.getLines().stream().collect(Collectors.toMap(CartLine::getRemoteCartLineId, line -> line));
    }

    private List<String> normalizeCodes(List<String> codes) {
        return codes == null ? null : codes.stream().filter(CartReplacementService::hasText)
                .map(String::trim).distinct().toList();
    }

    private boolean providerBound(Cart cart) {
        return hasText(cart.getRoutingScopeKey()) && !cart.getRoutingScopeKey().startsWith("LEGACY:");
    }

    private CartException ambiguousSnapshot() {
        return CartException.binding(CartException.BindingFailure.IDENTITY_MISMATCH,
                "Fresh remote cart state cannot be matched exactly to immutable local lines");
    }

    private LineIdentity identity(CartAddItem item) {
        List<String> options = item.selectedOptions().stream().map(this::option).sorted().toList();
        List<String> components = item.components().stream().map(this::component).sorted().toList();
        String sellingPlan = item.sellingPlan() == null ? "" : value(item.sellingPlan().groupId()) + '|'
                + value(item.sellingPlan().planId()) + '|'
                + item.sellingPlan().options().stream().map(this::option).sorted().collect(Collectors.joining(","));
        return new LineIdentity(value(item.productId()), value(item.productVariantId()), options, components,
                sellingPlan, item.quantity());
    }

    private String component(CartAddItem.Component component) {
        return value(component.productId()) + '|' + value(component.productVariantId()) + '|' + component.quantity()
                + '|' + component.selectedOptions().stream().map(this::option).sorted().collect(Collectors.joining(","));
    }

    private String option(CartAddItem.SelectedOption option) {
        return value(option.group()) + '|' + value(option.name()) + '|' + value(option.value());
    }

    private String option(CartAddItem.Option option) {
        return value(option.name()) + '|' + value(option.value());
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record LineIdentity(
            String productId, String variantId, List<String> options, List<String> components,
            String sellingPlan, Integer quantity
    ) {
    }
}
