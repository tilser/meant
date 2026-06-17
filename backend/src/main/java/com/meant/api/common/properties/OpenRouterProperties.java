package com.meant.api.common.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ai.openrouter")
public record OpenRouterProperties(

        @NotBlank
        String baseUrl,

        @NotNull
        String apiKey,

        @NotBlank
        String appTitle,

        @NotNull
        @Valid
        Models models
) {

    public record Models(
            @NotBlank
            String preferenceFilterParser,

            @NotBlank
            String productSearchQueryParser,

            @NotBlank
            String productRecommendationExplainer,

            @NotBlank
            String chatModel
    ) {
    }
}
