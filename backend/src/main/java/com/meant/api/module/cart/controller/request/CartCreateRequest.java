package com.meant.api.module.cart.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CartCreateRequest(
        UUID merchantId,
        String merchantDomain,
        @NotEmpty
        List<@Valid CartAddItemRequest> addItems,
        Map<String, Object> buyerIdentity,
        List<Map<String, Object>> deliveryAddressesToAdd,
        List<Map<String, Object>> deliveryAddressesToReplace,
        List<Map<String, Object>> selectedDeliveryOptions,
        List<@NotBlank String> discountCodes,
        List<@NotBlank String> giftCardCodes,
        String note
) {
}
