package com.meant.api.module.cart.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Creates a merchant cart from exact selected offers and optional buyer and fulfillment data.")
public record CartCreateRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty
        List<@NotNull @Valid CartAddItemRequest> addItems,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Valid
        CartBuyerIdentityRequest buyerIdentity,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotNull @Valid CartDeliveryAddressSelectionRequest> deliveryAddressesToAdd,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotNull @Valid CartDeliveryAddressSelectionRequest> deliveryAddressesToReplace,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotNull @Valid CartDeliveryOptionSelectionRequest> selectedDeliveryOptions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotBlank String> discountCodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<@NotBlank String> giftCardCodes,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String note
) {
}
