package com.meant.api.plugin.signing;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicSigningKey(
        String kid,
        SigningKeyPurpose purpose,
        SigningKeyStatus status,
        Map<String, Object> jwk,
        Instant advertiseUntil
) {

    public PublicSigningKey {
        kid = requireText(kid, "kid");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(status, "status must not be null");
        jwk = immutableLinkedMap(jwk);
    }

    private static Map<String, Object> immutableLinkedMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
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
