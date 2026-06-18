package com.meant.api.common.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rest-client")
public record RestClientProperties(

        @Positive
        @NotNull
        Integer connectTimeoutMilliseconds,

        @Positive
        @NotNull
        Integer readTimeoutMilliseconds
) {
}
