package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserProductDiscoveryResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record UserProductDiscoveryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserSavedProductResponse> savedProducts,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserProductSearchProductResponse> recentProducts
) {

    public static UserProductDiscoveryResponse from(UserProductDiscoveryResult result) {
        return new UserProductDiscoveryResponse(
                result.savedProducts().stream()
                        .map(UserSavedProductResponse::from)
                        .toList(),
                result.recentProducts().stream()
                        .map(UserProductSearchProductResponse::from)
                        .toList()
        );
    }
}
