package com.meant.api.module.cart.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UpdateCartCommand(
        @NotNull
        UUID cartId,
        @NotNull
        UUID userId,
        List<@NotNull @Valid AddItem> addItems,
        List<@Valid UpdateItem> updateItems,
        List<UUID> removeCartLineIds,
        List<String> removeRemoteCartLineIds,
        Map<String, Object> buyerIdentity,
        List<Map<String, Object>> deliveryAddressesToAdd,
        List<Map<String, Object>> deliveryAddressesToReplace,
        List<Map<String, Object>> selectedDeliveryOptions,
        List<@NotBlank String> discountCodes,
        List<@NotBlank String> giftCardCodes,
        String note,
        String buyerIp
) {

    public UpdateCartCommand(
            UUID cartId,
            UUID userId,
            List<AddItem> addItems,
            List<UpdateItem> updateItems,
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
        this(cartId, userId, addItems, updateItems, removeCartLineIds, removeRemoteCartLineIds, buyerIdentity,
                deliveryAddressesToAdd, deliveryAddressesToReplace, selectedDeliveryOptions, discountCodes,
                giftCardCodes, note, null);
    }

    public record AddItem(
            @NotBlank
            String offerKey,
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
