package com.meant.api.module.cart.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CartUpdateRequest(
        List<@Valid CartAddItemRequest> addItems,
        List<@Valid CartUpdateItemRequest> updateItems,
        List<UUID> removeCartLineIds,
        List<String> removeRemoteCartLineIds,
        Map<String, Object> buyerIdentity,
        List<Map<String, Object>> deliveryAddressesToAdd,
        List<Map<String, Object>> deliveryAddressesToReplace,
        List<Map<String, Object>> selectedDeliveryOptions,
        List<@NotBlank String> discountCodes,
        List<@NotBlank String> giftCardCodes,
        String note
) {
}
