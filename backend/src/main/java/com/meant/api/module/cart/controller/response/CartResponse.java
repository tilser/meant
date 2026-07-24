package com.meant.api.module.cart.controller.response;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.service.BuyerSafeRoutingScopeKey;
import com.meant.api.module.cart.service.dto.CartResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID cartId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String merchantDomain,
        @Schema(description = "Immutable commerce provider scope", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String provider,
        @Schema(description = "Verified local integration route when one exists",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID merchantIntegrationId,
        @Schema(description = "Provider-scoped external seller identity",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String externalMerchantId,
        @Schema(description = "Opaque buyer-safe remote-cart seller/provider scope",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String routingScopeKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String remoteCartId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String checkoutUrl,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String continueUrl,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String instructions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer totalQuantity,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String totalAmount,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String subtotalAmount,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String currency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean active,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant remoteCreatedAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant remoteUpdatedAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
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
                result.provider(),
                result.merchantIntegrationId(),
                result.externalMerchantId(),
                BuyerSafeRoutingScopeKey.project(result.routingScopeKey()),
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
