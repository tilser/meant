package com.meant.api.module.user.service.dto;

import java.util.List;

public record UserProductDiscoveryResult(
        List<UserSavedProductResult> savedProducts,
        List<UserProductSearchProductResult> recentProducts
) {
}
