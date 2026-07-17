package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A stable, product-scoped preference reused by catalog qualification")
public record UserProductSearchPreferenceResponse(
        @Schema(
                description = "Normalized product scope where the preference applies, for example footwear",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String scope,
        @Schema(allowableValues = "SIZE", requiredMode = Schema.RequiredMode.REQUIRED)
        UserProductSearchAttributeName attributeName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> values
) {

    public static UserProductSearchPreferenceResponse from(UserProductSearchPreferenceResult result) {
        return new UserProductSearchPreferenceResponse(result.scope(), result.attributeName(), result.values());
    }
}
