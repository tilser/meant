package com.meant.api.module.agent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentStreamAdmissionProperties;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AgentEventStreamAdmissionTest {

    @Test
    void capsStreamsPerRunAndReleasesThePermitExactlyOnce() {
        AgentEventStreamAdmission admission = admission();
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        var first = admission.acquire(userId, runId);
        var second = admission.acquire(userId, runId);

        assertThatThrownBy(() -> admission.acquire(userId, runId))
                .isInstanceOfSatisfying(AgentException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AGENT_STREAM_LIMIT);
                });

        first.close();
        first.close();
        try (var replacement = admission.acquire(userId, runId)) {
            assertThat(admission.activeStreams(userId, runId))
                    .isEqualTo(2);
        }
        second.close();

        assertThat(admission.activeStreams(userId)).isZero();
        assertThat(admission.trackedUsers()).isZero();
        assertThat(admission.trackedRuns()).isZero();
    }

    @Test
    void capsTotalStreamsAcrossRunsForOneUser() {
        AgentEventStreamAdmission admission = admission();
        UUID userId = UUID.randomUUID();
        var permits = new ArrayList<AgentEventStreamAdmission.Permit>();
        for (int index = 0; index < 4; index++) {
            permits.add(admission.acquire(userId, UUID.randomUUID()));
        }

        assertThatThrownBy(() -> admission.acquire(userId, UUID.randomUUID()))
                .isInstanceOf(AgentException.class);

        permits.forEach(AgentEventStreamAdmission.Permit::close);
        assertThat(admission.trackedUsers()).isZero();
        assertThat(admission.trackedRuns()).isZero();
    }

    private AgentEventStreamAdmission admission() {
        return new AgentEventStreamAdmission(new AgentStreamAdmissionProperties(4, 2));
    }
}
