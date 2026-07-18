package com.meant.api.module.cart.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Schema(description = "Partially updates a cart while preserving the merchant's complete remote cart state.")
public record CartUpdateRequest(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotNull @Valid CartAddItemRequest> addItems,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@Valid CartUpdateItemRequest> updateItems,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<UUID> removeCartLineIds,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<String> removeRemoteCartLineIds,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Valid
        CartBuyerIdentityRequest buyerIdentity,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotNull @Valid CartDeliveryAddressSelectionRequest> deliveryAddressesToAdd,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotNull @Valid CartDeliveryAddressSelectionRequest> deliveryAddressesToReplace,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotNull @Valid CartDeliveryOptionSelectionRequest> selectedDeliveryOptions,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotBlank String> discountCodes,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotBlank String> giftCardCodes,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String note
) {
}
