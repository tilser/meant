package com.meant.api.common.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateLimitPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsEndpointPathWithoutLeadingSlash() {
        RateLimitProperties properties = properties("api/users/me/product-searches");

        assertThat(validator.validate(properties))
                .anySatisfy(violation -> assertThat(violation.getMessage()).isEqualTo("Path must start with '/'"));
    }

    private static RateLimitProperties properties(String path) {
        return new RateLimitProperties(
                true,
                new RateLimitProperties.ExpensiveEndpoints(
                        List.of(new RateLimitProperties.Endpoint("POST", path)),
                        List.of(new RateLimitProperties.Limit(
                                "per-minute",
                                10,
                                10,
                                Duration.ofMinutes(1)
                        )),
                        new RateLimitProperties.BucketCache(1_000L, Duration.ofHours(25))
                )
        );
    }
}
