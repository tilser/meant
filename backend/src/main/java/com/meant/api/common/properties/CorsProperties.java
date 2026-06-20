package com.meant.api.common.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        @NotEmpty List<@NotBlank String> allowedOrigins
) {

    @AssertTrue(message = "allowedOrigins must not contain wildcard origins")
    public boolean hasNoWildcardOrigins() {
        return allowedOrigins != null && allowedOrigins.stream()
                .map(String::trim)
                .noneMatch("*"::equals);
    }
}
