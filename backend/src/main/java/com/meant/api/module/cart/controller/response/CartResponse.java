package com.meant.api.module.cart.controller.response;

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
        String instructions,
        Integer totalQuantity,
        String totalAmount,
        String subtotalAmount,
        String currency,
        boolean active,
        Instant remoteCreatedAt,
        Instant remoteUpdatedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant refreshedAt,
        List<CartLineResponse> lines
) {

    public static CartResponse from(CartResult result) {
        return new CartResponse(
                result.cartId(),
                result.merchantId(),
                result.merchantDomain(),
                result.endpoint(),
                result.remoteCartId(),
                result.checkoutUrl(),
                result.instructions(),
                result.totalQuantity(),
                result.totalAmount(),
                result.subtotalAmount(),
                result.currency(),
                result.active(),
                result.remoteCreatedAt(),
                result.remoteUpdatedAt(),
                result.createdAt(),
                result.updatedAt(),
                result.refreshedAt(),
                result.lines().stream().map(CartLineResponse::from).toList()
        );
    }
}
