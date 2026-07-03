package com.meant.api.module.discount.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SearchDiscountCodesCommand(
        @NotNull
        UUID userId,
        UUID merchantId,
        String merchantDomain,
        @NotEmpty
        List<@Valid Item> items,
        Map<String, Object> buyerIdentity,
        List<Map<String, Object>> deliveryAddressesToAdd,
        List<Map<String, Object>> deliveryAddressesToReplace,
        List<Map<String, Object>> selectedDeliveryOptions
) {

    @AssertTrue(message = "merchantId or merchantDomain is required")
    public boolean hasMerchantIdentity() {
        return merchantId != null || (merchantDomain != null && !merchantDomain.isBlank());
    }

    public record Item(
            @NotBlank
            String productVariantId,
            @NotNull
            @Positive
            Integer quantity
    ) {
    }
}
