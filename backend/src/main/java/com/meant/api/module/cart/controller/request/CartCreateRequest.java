package com.meant.api.module.cart.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CartCreateRequest(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String merchantDomain,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty
        List<@Valid CartAddItemRequest> addItems,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Map<String, Object> buyerIdentity,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<Map<String, Object>> deliveryAddressesToAdd,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<Map<String, Object>> deliveryAddressesToReplace,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<Map<String, Object>> selectedDeliveryOptions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotBlank String> discountCodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotBlank String> giftCardCodes,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String note
) {
}
