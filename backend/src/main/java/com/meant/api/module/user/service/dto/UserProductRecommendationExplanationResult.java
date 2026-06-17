package com.meant.api.module.user.service.dto;

import java.util.List;

public record UserProductRecommendationExplanationResult(
        String productKey,
        String productHash,
        String whyMeantForYou,
        List<String> matchedFilterIds,
        List<String> missedFilterIds
) {
}
