package com.meant.api.module.merchant.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MerchantCartCreateRequest(
        UUID merchantId,
        String merchantDomain,
        @NotEmpty
        List<@Valid MerchantCartAddItemRequest> addItems,
        Map<String, Object> buyerIdentity,
        List<Map<String, Object>> deliveryAddressesToAdd,
        List<Map<String, Object>> deliveryAddressesToReplace,
        List<Map<String, Object>> selectedDeliveryOptions,
        List<String> discountCodes,
        List<String> giftCardCodes,
        String note
) {
}
