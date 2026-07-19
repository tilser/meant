package com.meant.api.module.agent.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("commerce.agent.stream-admission")
public record AgentStreamAdmissionProperties(
        @Min(1) int maximumPerUser,
        @Min(1) int maximumPerRun
) {

    @AssertTrue(message = "maximumPerRun must not exceed maximumPerUser")
    public boolean isRunLimitWithinUserLimit() {
        return maximumPerRun <= maximumPerUser;
    }
}
