package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.entity.MerchantCart;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record MerchantCartResult(
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
        List<MerchantCartLineResult> lines
) {

    public static MerchantCartResult from(MerchantCart cart) {
        return new MerchantCartResult(
                cart.getId(),
                cart.getMerchant().getId(),
                cart.getMerchant().getDomain(),
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
                cart.getLines().stream()
                        .map(MerchantCartLineResult::from)
                        .sorted(Comparator.comparing(MerchantCartLineResult::createdAt))
                        .toList()
        );
    }
}
