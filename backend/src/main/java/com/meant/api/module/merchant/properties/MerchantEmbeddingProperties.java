package com.meant.api.module.merchant.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "merchant.embedding")
public record MerchantEmbeddingProperties(

        @NotBlank
        String voyageBaseUrl,

        @NotNull
        String apiKey,

        @NotBlank
        String model,

        @Positive
        @NotNull
        Integer dimension,

        @Positive
        @NotNull
        Integer batchSize,

        @Positive
        @NotNull
        Long fixedDelay
) {
}
