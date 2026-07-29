package com.meant.api.module.agent.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AgentPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsPositiveAgentDurations() {
        assertThat(validator.validate(properties(
                Duration.ofSeconds(300),
                Duration.ofSeconds(45),
                Duration.ofSeconds(120)
        ))).isEmpty();
    }

    @Test
    void rejectsAZeroAgentDuration() {
        assertThat(validator.validate(properties(
                Duration.ofSeconds(300),
                Duration.ofSeconds(45),
                Duration.ZERO
        ))).anySatisfy(violation -> assertThat(violation.getMessage())
                .isEqualTo("agent deadlines and timeouts must be positive"));
    }

    @Test
    void rejectsANegativeAgentDuration() {
        assertThat(validator.validate(properties(
                Duration.ofSeconds(300),
                Duration.ofSeconds(-1),
                Duration.ofSeconds(120)
        ))).anySatisfy(violation -> assertThat(violation.getMessage())
                .isEqualTo("agent deadlines and timeouts must be positive"));
    }

    @Test
    void rejectsARepeatedToolCallThresholdBelowTwo() {
        assertThat(validator.validate(properties(
                Duration.ofSeconds(300),
                Duration.ofSeconds(45),
                Duration.ofSeconds(120),
                1
        ))).anySatisfy(violation -> {
            assertThat(violation.getPropertyPath().toString())
                    .isEqualTo("repeatedIdenticalToolCallThreshold");
            assertThat(violation.getConstraintDescriptor().getAttributes())
                    .containsEntry("value", 2L);
        });
    }

    private AgentProperties properties(
            Duration runDeadline,
            Duration modelTimeout,
            Duration toolDeadline
    ) {
        return properties(runDeadline, modelTimeout, toolDeadline, 2);
    }

    private AgentProperties properties(
            Duration runDeadline,
            Duration modelTimeout,
            Duration toolDeadline,
            int repeatedIdenticalToolCallThreshold
    ) {
        return new AgentProperties(
                true,
                "primary-model",
                "fallback-model",
                "https://example.test/v1",
                "test-key",
                "Meant Test",
                "https://example.test",
                "test-v1",
                "test-v1",
                0,
                1024,
                8,
                20,
                6,
                4,
                40,
                64_000,
                24_000,
                repeatedIdenticalToolCallThreshold,
                runDeadline,
                modelTimeout,
                toolDeadline,
                Duration.ofMinutes(2),
                Duration.ofSeconds(5),
                128,
                Duration.ofDays(1),
                Duration.ofMinutes(5)
        );
    }
}
