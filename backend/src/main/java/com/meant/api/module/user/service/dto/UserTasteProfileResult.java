package com.meant.api.module.user.service.dto;

import java.util.List;

public record UserTasteProfileResult(
        String profileHash,
        List<UserTasteSignalResult> signals,
        List<UserTasteSuggestionResult> suggestions
) {
}
