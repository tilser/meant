package com.meant.api.module.cart.controller.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Selected delivery option for a merchant fulfillment group.")
public record CartDeliveryOptionSelectionRequest(
        @JsonAlias({"method_id", "fulfillment_method_id"})
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String methodId,
        @JsonAlias({"id", "deliveryGroupId", "group_id", "delivery_group_id"})
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String groupId,
        @JsonAlias({"optionHandle", "deliveryOptionHandle", "option_handle", "delivery_option_handle",
                "selected_option_id"})
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String selectedOptionId
) {
}
