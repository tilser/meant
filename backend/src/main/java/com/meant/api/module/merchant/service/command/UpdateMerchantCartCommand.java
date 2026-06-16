package com.meant.api.module.merchant.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UpdateMerchantCartCommand(
        @NotNull
        UUID cartId,
        List<@Valid AddItem> addItems,
        List<@Valid UpdateItem> updateItems,
        List<UUID> removeCartLineIds,
        List<String> removeRemoteCartLineIds,
        Map<String, Object> buyerIdentity,
        List<Map<String, Object>> deliveryAddressesToAdd,
        List<Map<String, Object>> deliveryAddressesToReplace,
        List<Map<String, Object>> selectedDeliveryOptions,
        List<String> discountCodes,
        List<String> giftCardCodes,
        String note
) {

    public record AddItem(
            @NotBlank
            String productVariantId,
            @Positive
            Integer quantity
    ) {
    }

    public record UpdateItem(
            UUID cartLineId,
            String remoteCartLineId,
            @Positive
            Integer quantity
    ) {
    }
}
