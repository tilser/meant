package com.meant.api.module.user.service.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record FindUserProductSearchQualificationByRequestQuery(
        @NotNull UUID userId,
        @NotNull UUID conversationId,
        UUID merchantId,
        @NotNull UUID requestId,
        @NotBlank @Size(max = 8000) String message
) {
}
