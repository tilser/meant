package com.meant.api.module.cart.controller.response;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.service.dto.CartResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(
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
        List<CartAppliedCodeResponse> appliedCodes,
        List<CartLineResponse> lines,
        List<CartDeliveryGroupResponse> deliveryGroups,
        List<CartMessageResponse> messages
) {

    public static CartResponse from(CartResult result) {
        return new CartResponse(
                result.cartId(),
                result.merchantId(),
                result.merchantDomain(),
                result.endpoint(),
                result.remoteCartId(),
                result.checkoutUrl(),
                result.continueUrl(),
                result.instructions(),
                result.totalQuantity(),
                result.totalAmount(),
                result.subtotalAmount(),
                result.currency(),
                result.active(),
                result.remoteCreatedAt(),
                result.remoteUpdatedAt(),
                result.expiresAt(),
                result.createdAt(),
                result.updatedAt(),
                result.refreshedAt(),
                safeNonNullList(result.appliedCodes()).stream().map(CartAppliedCodeResponse::from).toList(),
                safeNonNullList(result.lines()).stream().map(CartLineResponse::from).toList(),
                safeNonNullList(result.deliveryGroups()).stream()
                        .map(CartDeliveryGroupResponse::from)
                        .filter(group -> group != null)
                        .toList(),
                safeNonNullList(result.messages()).stream()
                        .map(CartMessageResponse::from)
                        .filter(message -> message != null)
                        .toList()
        );
    }
}
