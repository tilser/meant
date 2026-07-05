package com.meant.api.module.review.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "review.providers.okendo")
public record OkendoReviewProperties(

        @NotBlank
        String reviewsBaseUrl,

        @NotNull
        @Positive
        Integer defaultLimit
) {
}
