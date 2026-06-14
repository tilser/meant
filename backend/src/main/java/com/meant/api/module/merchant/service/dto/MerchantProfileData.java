package com.meant.api.module.merchant.service.dto;

import java.util.List;

public record MerchantProfileData(
        String name,
        String description,
        String about,
        String targetAudience,
        String profileQuestion,
        String profileAnswerRaw,
        List<String> categories,
        List<String> popularSearches
) {
}
