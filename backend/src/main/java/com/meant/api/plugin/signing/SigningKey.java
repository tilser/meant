package com.meant.api.plugin.signing;

import com.nimbusds.jose.jwk.ECKey;
import java.time.Instant;
import java.util.Objects;

public record SigningKey(
        String kid,
        SigningKeyPurpose purpose,
        SigningKeyStatus status,
        ECKey privateJwk,
        ECKey publicJwk,
        Instant advertiseUntil
) {

    public SigningKey {
        kid = requireText(kid, "kid");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(publicJwk, "publicJwk must not be null");
        if (privateJwk != null && !privateJwk.isPrivate()) {
            throw new IllegalArgumentException("privateJwk must contain private key material");
        }
        if (publicJwk.isPrivate()) {
            throw new IllegalArgumentException("publicJwk must not contain private key material");
        }
    }

    public boolean canSign() {
        return status == SigningKeyStatus.ACTIVE && privateJwk != null;
    }

    public boolean canAdvertise(Instant now) {
        if (status == SigningKeyStatus.ACTIVE) {
            return true;
        }
        if (status != SigningKeyStatus.RETIRING) {
            return false;
        }
        return advertiseUntil == null || !advertiseUntil.isBefore(now);
    }

    public PublicSigningKey toPublicSigningKey() {
        return new PublicSigningKey(kid, purpose, status, JsonWebKey.from(publicJwk), advertiseUntil);
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
