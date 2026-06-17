package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserProductDiscoveryResult;
import java.util.List;

public record UserProductDiscoveryResponse(
        List<UserSavedProductResponse> savedProducts,
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
