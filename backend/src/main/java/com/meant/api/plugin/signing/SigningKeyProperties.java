package com.meant.api.plugin.signing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ucp.signing")
public record SigningKeyProperties(
        @NotNull Duration signatureTtl,
        @NotNull Duration clockSkew,
        List<@Valid Key> keys
) {

    public SigningKeyProperties {
        keys = keys == null ? List.of() : List.copyOf(keys);
    }

    @AssertTrue(message = "signatureTtl must be positive")
    public boolean hasPositiveSignatureTtl() {
        return signatureTtl != null && !signatureTtl.isZero() && !signatureTtl.isNegative();
    }

    @AssertTrue(message = "clockSkew must not be negative")
    public boolean hasNonNegativeClockSkew() {
        return clockSkew != null && !clockSkew.isNegative();
    }

    public record Key(
            @NotBlank String kid,
            @NotNull SigningKeyPurpose purpose,
            @NotNull SigningKeyStatus status,
            char[] privateJwk,
            char[] publicJwk,
            Instant advertiseUntil
    ) {

        @AssertTrue(message = "privateJwk or publicJwk must be configured")
        public boolean hasKeyMaterial() {
            return hasText(privateJwk) || hasText(publicJwk);
        }

        private static boolean hasText(char[] value) {
            if (value == null || value.length == 0) {
                return false;
            }
            for (char ch : value) {
                if (!Character.isWhitespace(ch)) {
                    return true;
                }
            }
            return false;
        }
    }
}
