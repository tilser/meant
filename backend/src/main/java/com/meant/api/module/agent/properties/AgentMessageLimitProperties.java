package com.meant.api.module.agent.properties;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("commerce.agent.message-limit")
public record AgentMessageLimitProperties(
        @Min(1) int maximumPerUserPerDay
) {
}
