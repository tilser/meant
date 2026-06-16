package com.meant.api.module.cart.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateCartCommand(
        UUID merchantId,
        String merchantDomain,
        @NotEmpty
        List<@Valid AddItem> addItems,
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
}
