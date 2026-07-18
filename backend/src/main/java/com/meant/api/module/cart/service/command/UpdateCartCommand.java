package com.meant.api.module.cart.service.command;

import com.meant.api.module.cart.service.dto.CartBuyerIdentityInput;
import com.meant.api.module.cart.service.dto.CartDeliveryAddressSelectionInput;
import com.meant.api.module.cart.service.dto.CartDeliveryOptionSelectionInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
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
        @Valid CartBuyerIdentityInput buyerIdentity,
        List<@NotNull @Valid CartDeliveryAddressSelectionInput> deliveryAddressesToAdd,
        List<@NotNull @Valid CartDeliveryAddressSelectionInput> deliveryAddressesToReplace,
        List<@NotNull @Valid CartDeliveryOptionSelectionInput> selectedDeliveryOptions,
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
            CartBuyerIdentityInput buyerIdentity,
            List<CartDeliveryAddressSelectionInput> deliveryAddressesToAdd,
            List<CartDeliveryAddressSelectionInput> deliveryAddressesToReplace,
            List<CartDeliveryOptionSelectionInput> selectedDeliveryOptions,
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
