package com.meant.api.module.agent.service.query;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GetAgentSimilaritySearchQualificationQuery(
        @NotNull UUID qualificationId,
        @NotNull UUID userId,
        @NotNull UUID conversationId,
        UUID merchantId
) {
}
