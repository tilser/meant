package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeList;
import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.command.UpdateCartCommand;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartToolArguments;
import com.meant.api.plugin.cart.common.dto.CartUpdateItem;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.update.dto.CartReplacementState;
import com.meant.api.plugin.cart.update.dto.UpdateCartRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
            Map<String, Object> derivedContext,
            UcpCartResponse currentRemote
    ) {
        Map<UUID, CartLine> local = localLines(cart);
        Map<String, CartLine> remote = remoteLines(cart);
        List<CartUpdateItem> removals = removeItems(command, local, remote);
        List<CartUpdateItem> updates = safeList(command.updateItems()).stream()
                .map(item -> updateItem(item, local, remote))
                .toList();
        CartReplacementState replacement = providerBound(cart)
                ? replacementState(cart, command, addedItems, derivedContext, currentRemote)
                : null;
        return new UpdateCartRequest(
                cart.getRemoteCartId(), addedItems, updates,
                removals.stream().map(CartUpdateItem::id).toList(), removals,
                command.buyerIdentity(), derivedContext,
                command.deliveryAddressesToAdd(), command.deliveryAddressesToReplace(),
                command.selectedDeliveryOptions(), normalizeCodes(command.discountCodes()),
                normalizeCodes(command.giftCardCodes()), command.note(), replacement);
    }

    public boolean proves(UcpCartResponse response, CartReplacementState intended) {
        if (response == null || response.cart() == null || intended == null) {
            return false;
        }
        List<LineIdentity> expected = intended.lineItems().stream().map(this::identity).sorted(ORDER).toList();
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
        actual.sort(ORDER);
        return expected.equals(actual);
    }

    private CartReplacementState replacementState(
            Cart cart,
            UpdateCartCommand command,
            List<CartAddItem> addedItems,
            Map<String, Object> derivedContext,
            UcpCartResponse response
    ) {
        UcpCartResponse.Cart remote = response == null ? null : response.cart();
        if (remote == null) {
            throw CartException.binding(CartException.BindingFailure.PROVIDER_FAILURE,
                    "A fresh remote cart snapshot is required before replacement");
        }
        List<CartAddItem> lines = replacementLines(cart, command, addedItems, remote);
        Map<String, Object> context = new LinkedHashMap<>(remote.context());
        if (derivedContext != null) {
            context.putAll(derivedContext);
        }
        List<String> discounts = command.discountCodes() == null
                ? remoteDiscountCodes(remote) : normalizeCodes(command.discountCodes());
        List<String> giftCards = command.giftCardCodes() == null
                ? remoteGiftCardCodes(remote) : normalizeCodes(command.giftCardCodes());
        return new CartReplacementState(
                lines,
                command.buyerIdentity() == null ? remote.buyer() : command.buyerIdentity(),
                context,
                remote.signals(),
                fulfillmentReplacementService.merge(
                        remote.fulfillment(), command.deliveryAddressesToAdd(),
                        command.deliveryAddressesToReplace(), command.selectedDeliveryOptions()),
                discounts == null || discounts.isEmpty() ? null : new CartToolArguments.Discounts(discounts),
                giftCards,
                command.note() == null ? remote.note() : command.note());
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

    private static final Comparator<LineIdentity> ORDER = Comparator.comparing(LineIdentity::sortKey);

    private record LineIdentity(
            String productId, String variantId, List<String> options, List<String> components,
            String sellingPlan, Integer quantity
    ) {
        String sortKey() {
            return productId + '\u0000' + variantId + '\u0000' + options + '\u0000' + components
                    + '\u0000' + sellingPlan + '\u0000' + quantity;
        }
    }
}
