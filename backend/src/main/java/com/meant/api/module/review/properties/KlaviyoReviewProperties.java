package com.meant.api.module.review.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "review.providers.klaviyo")
public record KlaviyoReviewProperties(

        @NotBlank
        String reviewsBaseUrl,

        @NotNull
        @Positive
        Integer defaultLimit
) {
}
