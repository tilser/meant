package com.meant.api.module.agent.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BindAgentSimilaritySearchQualificationCommand(
        @NotNull UUID qualificationId,
        @NotNull UUID userId,
        @NotNull UUID conversationId,
        UUID merchantId,
        @NotBlank @Size(max = 200) String canonicalProductKey,
        UUID inventoryItemId,
        String anchorLabel,
        @NotBlank @Size(max = 8000) String initialUserText
) {

    public BindAgentSimilaritySearchQualificationCommand {
        canonicalProductKey = canonicalProductKey == null ? null : canonicalProductKey.trim();
        anchorLabel = anchorLabel == null || anchorLabel.isBlank() ? null : anchorLabel.trim();
        initialUserText = initialUserText == null ? null : initialUserText.trim();
    }
}
