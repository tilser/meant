package com.meant.api.module.agent.service.dto;

import com.meant.api.module.cart.service.dto.CartLineResult;
import com.meant.api.module.cart.service.dto.CartResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentCartResult(
        List<Cart> carts,
        List<Failure> failures
) {

    public AgentCartResult {
        carts = carts == null ? List.of() : List.copyOf(carts);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public static AgentCartResult success(List<CartResult> carts) {
        return new AgentCartResult(carts.stream().map(Cart::from).toList(), List.of());
    }

    public record Cart(
            UUID cartId,
            UUID merchantId,
            String merchantDomain,
            String provider,
            UUID merchantIntegrationId,
            String externalMerchantId,
            String routingScopeKey,
            String remoteCartId,
            String checkoutUrl,
            String continueUrl,
            Integer totalQuantity,
            String totalAmount,
            String subtotalAmount,
            String currency,
            Instant expiresAt,
            Instant updatedAt,
            List<Line> lines
    ) {
        public Cart {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }

        public static Cart from(CartResult result) {
            return new Cart(
                    result.cartId(),
                    result.merchantId(),
                    result.merchantDomain(),
                    result.provider(),
                    result.merchantIntegrationId(),
                    result.externalMerchantId(),
                    result.routingScopeKey(),
                    result.remoteCartId(),
                    result.checkoutUrl(),
                    result.continueUrl(),
                    result.totalQuantity(),
                    result.totalAmount(),
                    result.subtotalAmount(),
                    result.currency(),
                    result.expiresAt(),
                    result.updatedAt(),
                    result.lines().stream().limit(50).map(Line::from).toList()
            );
        }
    }

    public record Line(
            UUID cartLineId,
            String remoteCartLineId,
            String productId,
            String productTitle,
            String productVariantId,
            String variantTitle,
            Integer quantity,
            String totalAmount,
            String subtotalAmount,
            String currency,
            String offerKey,
            String provider,
            UUID merchantIntegrationId,
            String externalMerchantId
    ) {
        public static Line from(CartLineResult result) {
            return new Line(
                    result.cartLineId(),
                    result.remoteCartLineId(),
                    result.productId(),
                    result.productTitle(),
                    result.productVariantId(),
                    result.variantTitle(),
                    result.quantity(),
                    result.totalAmount(),
                    result.subtotalAmount(),
                    result.currency(),
                    result.offerKey(),
                    result.provider(),
                    result.merchantIntegrationId(),
                    result.externalMerchantId()
            );
        }
    }

    public record Failure(
            String routingScopeKey,
            String provider,
            String merchantDomain,
            String safeMessage
    ) {
    }
}
