package com.meant.api.module.cart.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CartUpdateRequest(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@Valid CartAddItemRequest> addItems,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@Valid CartUpdateItemRequest> updateItems,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<UUID> removeCartLineIds,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<String> removeRemoteCartLineIds,
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
