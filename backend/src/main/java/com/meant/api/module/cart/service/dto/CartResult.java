package com.meant.api.module.cart.service.dto;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.entity.Cart;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record CartResult(
        UUID cartId,
        UUID merchantId,
        String merchantDomain,
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
        return new CartResult(
                cart.getId(),
                cart.getMerchantId(),
                cart.getMerchantDomain(),
                cart.getEndpoint(),
                cart.getRemoteCartId(),
                cart.getCheckoutUrl(),
                cart.getContinueUrl(),
                cart.getInstructions(),
                cart.getTotalQuantity(),
                cart.getTotalAmount(),
                cart.getSubtotalAmount(),
                cart.getCurrency(),
                cart.isActive(),
                cart.getRemoteCreatedAt(),
                cart.getRemoteUpdatedAt(),
                cart.getExpiresAt(),
                cart.getCreatedAt(),
                cart.getUpdatedAt(),
                cart.getRefreshedAt(),
                cart.getAppliedCodes().stream()
                        .sorted(Comparator.comparingInt(appliedCode ->
                                appliedCode.getDisplayOrder() == null ? Integer.MAX_VALUE : appliedCode.getDisplayOrder()))
                        .map(CartAppliedCodeResult::from)
                        .toList(),
                cart.getLines().stream()
                        .map(CartLineResult::from)
                        .sorted(Comparator.comparing(CartLineResult::createdAt))
                        .toList(),
                safeNonNullList(deliveryGroups),
                safeNonNullList(messages)
        );
    }
}
