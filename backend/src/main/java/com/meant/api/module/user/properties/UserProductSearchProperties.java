package com.meant.api.module.user.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "user.product-search")
public record UserProductSearchProperties(

        @NotBlank
        String searchVersion,

        @NotBlank
        String queryParserPromptVersion,

        @NotBlank
        String explanationPromptVersion,

        @NotNull
        Duration cacheTtl
) {
}
