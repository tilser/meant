package com.meant.api.module.cart.service.dto;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.entity.Cart;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

public record CartResult(
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
        List<CartAppliedCodeResult> appliedCodes,
        List<CartLineResult> lines,
        List<CartDeliveryGroupResult> deliveryGroups
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static CartResult from(Cart cart) {
        return new CartResult(
                cart.getId(),
                cart.getMerchantId(),
                cart.getMerchantDomain(),
                cart.getEndpoint(),
                cart.getRemoteCartId(),
                cart.getCheckoutUrl(),
                cart.getInstructions(),
                cart.getTotalQuantity(),
                cart.getTotalAmount(),
                cart.getSubtotalAmount(),
                cart.getCurrency(),
                cart.isActive(),
                cart.getRemoteCreatedAt(),
                cart.getRemoteUpdatedAt(),
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
                deliveryGroups(cart.getRawCartResponse())
        );
    }

    private static List<CartDeliveryGroupResult> deliveryGroups(String rawCartResponse) {
        if (rawCartResponse == null || rawCartResponse.isBlank()) {
            return List.of();
        }
        try {
            CartToolResponse response = OBJECT_MAPPER.readValue(rawCartResponse, CartToolResponse.class);
            if (response == null || response.cart() == null) {
                return List.of();
            }
            return safeNonNullList(response.cart().deliveryGroups()).stream()
                    .map(CartDeliveryGroupResult::from)
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }
}
