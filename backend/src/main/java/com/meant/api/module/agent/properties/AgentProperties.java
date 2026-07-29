package com.meant.api.module.agent.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("commerce.agent")
public record AgentProperties(
        boolean enabled,
        @NotBlank String model,
        @NotBlank String fallbackModel,
        @NotBlank String baseUrl,
        String apiKey,
        @NotBlank String appTitle,
        @NotBlank String siteUrl,
        @NotBlank String promptVersion,
        @NotBlank String toolVersion,
        @DecimalMin("0.0") @DecimalMax("2.0") double temperature,
        @Min(1) int maximumOutputTokens,
        @Min(1) int maximumModelIterations,
        @Min(1) int maximumTotalToolInvocations,
        @Min(1) int maximumPerToolInvocations,
        @Min(1) int maximumParallelReadTools,
        @Min(1) int contextMessageBudget,
        @Min(4096) int contextCharacterBudget,
        @Min(256) int maximumResultCharacters,
        @Min(2) int repeatedIdenticalToolCallThreshold,
        @NotNull Duration runDeadline,
        @NotNull Duration modelTimeout,
        @NotNull Duration toolDeadline,
        @NotNull Duration eventStreamTimeout,
        @NotNull Duration eventPollInterval,
        @Min(1) int eventStreamQueueSize,
        @NotNull Duration eventRetention,
        @NotNull Duration staleRunAge
) {

    @AssertTrue(message = "agent deadlines and timeouts must be positive")
    public boolean hasPositiveDeadlines() {
        return positive(runDeadline)
                && positive(modelTimeout)
                && positive(toolDeadline)
                && positive(eventStreamTimeout)
                && positive(eventPollInterval)
                && positive(eventRetention)
                && positive(staleRunAge);
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
