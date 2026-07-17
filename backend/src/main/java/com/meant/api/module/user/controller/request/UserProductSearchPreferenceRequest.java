package com.meant.api.module.user.controller.request;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "A stable, product-scoped search preference learned from or edited by the user")
public record UserProductSearchPreferenceRequest(
        @Schema(
                description = "Normalized product scope where the preference applies, for example footwear",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 80)
        String scope,
        @Schema(
                description = "Stable Shopify product attribute represented by this preference",
                allowableValues = "SIZE",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        UserProductSearchAttributeName attributeName,
        @Schema(
                description = "Confirmed values to reuse for searches in the same product scope",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotEmpty
        @Size(max = 10)
        List<@NotBlank @Size(max = 120) String> values
) {

    @AssertTrue(message = "Only stable size preferences can be saved")
    public boolean isStableAttribute() {
        return attributeName == null || attributeName == UserProductSearchAttributeName.SIZE;
    }
}
