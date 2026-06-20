package com.meant.api.module.merchant.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "merchant.identity-linking")
public record MerchantIdentityLinkingProperties(
        @NotBlank String clientId,
        String clientSecret,
        @NotNull URI redirectUri,
        @NotBlank String tokenEncryptionSecret,
        @NotEmpty List<@NotBlank String> defaultScopes,
        @NotNull Duration refreshSkew
) {
}
