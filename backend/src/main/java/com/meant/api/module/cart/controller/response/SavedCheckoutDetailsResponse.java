package com.meant.api.module.cart.controller.response;

import com.meant.api.module.user.service.dto.UserCheckoutDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Previously supplied contact and delivery details available for explicit reuse.")
public record SavedCheckoutDetailsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SavedCheckoutBuyerResponse buyer,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SavedCheckoutShippingAddressResponse shippingAddress,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "When these saved checkout details were last changed."
        )
        Instant updatedAt
) {

    public static SavedCheckoutDetailsResponse from(UserCheckoutDetailsResult details) {
        return details == null
                ? null
                : new SavedCheckoutDetailsResponse(
                        SavedCheckoutBuyerResponse.from(details),
                        SavedCheckoutShippingAddressResponse.from(details),
                        details.updatedAt()
                );
    }
}
