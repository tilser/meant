package com.meant.api.module.cart.controller.response;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.service.dto.CartResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID cartId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String merchantDomain,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String endpoint,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String remoteCartId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String checkoutUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String continueUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String instructions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer totalQuantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String totalAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String subtotalAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String currency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant remoteCreatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant remoteUpdatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant expiresAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant refreshedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<CartAppliedCodeResponse> appliedCodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<CartLineResponse> lines,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<CartDeliveryGroupResponse> deliveryGroups,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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
