package com.meant.api.module.user.service.dto;

public record UserProductSearchQualificationModelResult(
        UserProductSearchQualificationPlan plan,
        String model,
        String promptVersion
) {
}
