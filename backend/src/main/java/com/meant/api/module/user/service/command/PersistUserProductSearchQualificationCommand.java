package com.meant.api.module.user.service.command;

import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record PersistUserProductSearchQualificationCommand(
        @NotNull UUID qualificationId,
        @NotNull UUID userId,
        @NotNull UUID conversationId,
        UUID merchantId,
        @NotBlank String originalQuery,
        Instant expectedUpdatedAt,
        @NotNull UserProductSearchQualificationStatus status,
        @NotNull UserProductSearchQualificationPlan plan,
        @NotBlank String model,
        @NotBlank String promptVersion,
        UUID requestId,
        @Size(max = 8000) String requestMessage
) {

    public PersistUserProductSearchQualificationCommand {
        if ((requestId == null) != (requestMessage == null || requestMessage.isBlank())) {
            throw new IllegalArgumentException(
                    "Qualification request ID and message must either both be present or both be absent");
        }
        requestMessage = requestMessage == null ? null : requestMessage.trim();
    }
}
