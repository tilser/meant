package com.meant.api.plugin.signing;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicSigningKey(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String kid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SigningKeyPurpose purpose,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SigningKeyStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        JsonWebKey jwk,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant advertiseUntil
) {

    public PublicSigningKey {
        kid = requireText(kid, "kid");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(jwk, "jwk must not be null");
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
