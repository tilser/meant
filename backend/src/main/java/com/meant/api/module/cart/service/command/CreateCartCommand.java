package com.meant.api.module.cart.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateCartCommand(
        @NotNull
        UUID userId,
        UUID merchantId,
        String merchantDomain,
        @NotEmpty
        List<@NotNull @Valid AddItem> addItems,
        Map<String, Object> buyerIdentity,
        List<Map<String, Object>> deliveryAddressesToAdd,
        List<Map<String, Object>> deliveryAddressesToReplace,
        List<Map<String, Object>> selectedDeliveryOptions,
        List<@NotBlank String> discountCodes,
        List<@NotBlank String> giftCardCodes,
        String note,
        String buyerIp
) {

    public CreateCartCommand(
            UUID userId,
            UUID merchantId,
            String merchantDomain,
            List<AddItem> addItems,
            Map<String, Object> buyerIdentity,
            List<Map<String, Object>> deliveryAddressesToAdd,
            List<Map<String, Object>> deliveryAddressesToReplace,
            List<Map<String, Object>> selectedDeliveryOptions,
            List<String> discountCodes,
            List<String> giftCardCodes,
            String note
    ) {
        this(userId, merchantId, merchantDomain, addItems, buyerIdentity, deliveryAddressesToAdd,
                deliveryAddressesToReplace, selectedDeliveryOptions, discountCodes, giftCardCodes, note, null);
    }

    public record AddItem(
            @NotBlank
            String offerKey,
            @Positive
            Integer quantity
    ) {
    }
}
