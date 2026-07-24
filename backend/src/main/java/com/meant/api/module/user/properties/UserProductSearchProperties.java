package com.meant.api.module.user.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
        Duration cacheTtl,

        @NotNull
        Duration qualificationPendingTtl,

        @NotNull
        Duration productDetailSessionTtl,

        @Positive
        long productDetailSessionMaximumSize,

        @NotNull
        Duration streamTimeout,

        @Positive
        int streamQueueCapacity,

        @Positive
        int discoveryRecentSearchLimit,

        @Positive
        int discoveryRecentProductLimit,

        @NotNull
        Duration popularSearchWindow,

        @NotNull
        Duration popularSearchFallbackWindow,

        @Positive
        int popularSearchLimit,

        @Positive
        int popularSearchMinDistinctUsers,

        @Positive
        int popularSearchMaxDisplayLength
) {
}
