package com.meant.api.module.cart.service.dto;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.support.UcpCartMoney;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record CartResult(
        UUID cartId,
        UUID merchantId,
        String merchantDomain,
        String provider,
        UUID merchantIntegrationId,
        String externalMerchantId,
        String routingScopeKey,
        String endpoint,
        String remoteCartId,
        String checkoutUrl,
        String continueUrl,
        String instructions,
        Integer totalQuantity,
        String totalAmount,
        String subtotalAmount,
        String currency,
        boolean active,
        Instant remoteCreatedAt,
        Instant remoteUpdatedAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant refreshedAt,
        List<CartAppliedCodeResult> appliedCodes,
        List<CartLineResult> lines,
        List<CartDeliveryGroupResult> deliveryGroups,
        List<CartMessageResult> messages
) {

    public static CartResult from(
            Cart cart,
            List<CartDeliveryGroupResult> deliveryGroups,
            List<CartMessageResult> messages
    ) {
        return from(cart, null, deliveryGroups, messages);
    }

    public static CartResult from(
            Cart cart,
            UcpCartResponse currentResponse,
            List<CartDeliveryGroupResult> deliveryGroups,
            List<CartMessageResult> messages
    ) {
        UcpCartResponse.Cart currentCart = currentResponse == null ? null : currentResponse.cart();
        UcpCartResponse.Money currentTotal = currentCart == null || currentCart.cost() == null
                ? null : currentCart.cost().totalAmount();
        UcpCartResponse.Money currentSubtotal = currentCart == null || currentCart.cost() == null
                ? null : currentCart.cost().subtotalAmount();
        return new CartResult(
                cart.getId(),
                cart.getMerchantId(),
                cart.getMerchantDomain(),
                cart.getProvider(),
                cart.getMerchantIntegrationId(),
                cart.getExternalMerchantId(),
                cart.getRoutingScopeKey(),
                cart.getEndpoint(),
                cart.getRemoteCartId(),
                currentCart == null ? cart.getCheckoutUrl() : currentCart.checkoutUrl(),
                currentCart == null ? cart.getContinueUrl() : currentCart.continueUrl(),
                currentResponse == null ? cart.getInstructions() : currentResponse.instructions(),
                currentCart == null || currentCart.totalQuantity() == null
                        ? cart.getTotalQuantity() : currentCart.totalQuantity(),
                currentCart == null ? cart.getTotalAmount() : UcpCartMoney.displayAmount(currentTotal),
                currentCart == null ? cart.getSubtotalAmount() : UcpCartMoney.displayAmount(currentSubtotal),
                currentCart == null
                        ? cart.getCurrency()
                        : UcpCartMoney.currency(currentTotal, currentSubtotal, cart.getCurrency()),
                cart.isActive(),
                currentCart == null ? cart.getRemoteCreatedAt() : currentCart.createdAt(),
                currentCart == null ? cart.getRemoteUpdatedAt() : currentCart.updatedAt(),
                currentCart == null ? cart.getExpiresAt() : currentCart.expiresAt(),
                cart.getCreatedAt(),
                cart.getUpdatedAt(),
                cart.getRefreshedAt(),
                cart.getAppliedCodes().stream()
                        .sorted(Comparator.comparingInt(appliedCode ->
                                appliedCode.getDisplayOrder() == null ? Integer.MAX_VALUE : appliedCode.getDisplayOrder()))
                        .map(CartAppliedCodeResult::from)
                        .toList(),
                cart.getLines().stream()
                        .map(line -> CartLineResult.from(line, currentLine(currentCart, line.getRemoteCartLineId())))
                        .sorted(Comparator.comparing(CartLineResult::createdAt))
                        .toList(),
                safeNonNullList(deliveryGroups),
                safeNonNullList(messages)
        );
    }

    private static UcpCartResponse.Line currentLine(UcpCartResponse.Cart currentCart, String remoteCartLineId) {
        if (currentCart == null) {
            return null;
        }
        return safeNonNullList(currentCart.lines()).stream()
                .filter(line -> line != null && remoteCartLineId.equals(line.id()))
                .findFirst()
                .orElse(null);
    }
}
