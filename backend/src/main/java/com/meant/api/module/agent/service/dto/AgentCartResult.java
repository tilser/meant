package com.meant.api.module.agent.service.dto;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.constant.CartAppliedCodeType;
import com.meant.api.module.cart.service.dto.CartAppliedCodeResult;
import com.meant.api.module.cart.service.dto.CartDeliveryGroupResult;
import com.meant.api.module.cart.service.dto.CartDeliveryMoneyResult;
import com.meant.api.module.cart.service.dto.CartDeliveryOptionResult;
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
            List<AppliedCode> appliedCodes,
            List<Line> lines,
            List<DeliveryGroup> deliveryGroups
    ) {
        public Cart {
            appliedCodes = appliedCodes == null ? List.of() : List.copyOf(appliedCodes);
            lines = lines == null ? List.of() : List.copyOf(lines);
            deliveryGroups = deliveryGroups == null ? List.of() : List.copyOf(deliveryGroups);
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
                    safeNonNullList(result.appliedCodes()).stream().map(AppliedCode::from).toList(),
                    safeNonNullList(result.lines()).stream().map(Line::from).toList(),
                    safeNonNullList(result.deliveryGroups()).stream()
                            .map(DeliveryGroup::from)
                            .filter(group -> group != null)
                            .toList()
            );
        }
    }

    public record AppliedCode(
            CartAppliedCodeType type,
            String code,
            String label,
            Boolean applicable,
            String amount,
            String currency
    ) {
        public static AppliedCode from(CartAppliedCodeResult result) {
            return new AppliedCode(
                    result.type(),
                    result.code(),
                    result.label(),
                    result.applicable(),
                    result.amount(),
                    result.currency()
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

    public record DeliveryGroup(
            String id,
            String handle,
            List<DeliveryOption> deliveryOptions,
            DeliveryOption selectedDeliveryOption
    ) {
        public DeliveryGroup {
            deliveryOptions = deliveryOptions == null ? List.of() : List.copyOf(deliveryOptions);
        }

        public static DeliveryGroup from(CartDeliveryGroupResult result) {
            if (result == null) {
                return null;
            }
            return new DeliveryGroup(
                    result.id(),
                    result.handle(),
                    safeNonNullList(result.deliveryOptions()).stream()
                            .map(DeliveryOption::from)
                            .filter(option -> option != null)
                            .toList(),
                    DeliveryOption.from(result.selectedDeliveryOption())
            );
        }
    }

    public record DeliveryOption(
            String handle,
            String title,
            String description,
            String code,
            DeliveryMoney cost,
            String deliveryMethodType,
            String deliveryEstimate,
            String estimatedDeliveryTime,
            Instant estimatedDeliveryAt,
            Boolean selected
    ) {
        public static DeliveryOption from(CartDeliveryOptionResult result) {
            if (result == null) {
                return null;
            }
            return new DeliveryOption(
                    result.handle(),
                    result.title(),
                    result.description(),
                    result.code(),
                    DeliveryMoney.from(result.cost()),
                    result.deliveryMethodType(),
                    result.deliveryEstimate(),
                    result.estimatedDeliveryTime(),
                    result.estimatedDeliveryAt(),
                    result.selected()
            );
        }
    }

    public record DeliveryMoney(String amount, String currency) {
        public static DeliveryMoney from(CartDeliveryMoneyResult result) {
            return result == null ? null : new DeliveryMoney(result.amount(), result.currency());
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
