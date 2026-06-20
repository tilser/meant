package com.meant.api.module.user.service.dto;

public record UserTasteSuggestionResult(
        String filterId,
        String label,
        String description,
        String reason,
        double score
) {
}
