package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.constant.AgentUserActionStatus;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.command.RecordAgentUserActionCommand;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.AgentUserActionReservation;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AgentUserActionServiceTest {

    @Test
    void timedOutMutationReturnsAnExplicitUncertainCodeSoTheClientReusesItsKey() {
        UUID actionId = UUID.randomUUID();
        AgentToolRegistry registry = mock(AgentToolRegistry.class);
        AgentUserActionPersistenceService persistence = mock(AgentUserActionPersistenceService.class);
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentToolSchemaValidator schema = mock(AgentToolSchemaValidator.class);
        CountDownLatch blocked = new CountDownLatch(1);
        AgentTool tool = new AgentTool() {
            @Override
            public AgentToolDescriptor descriptor() {
                return new AgentToolDescriptor(
                        "prepare_carts",
                        "Prepare carts",
                        "{\"type\":\"object\"}",
                        "v1",
                        AgentToolRisk.REVERSIBLE_MUTATION
                );
            }

            @Override
            public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
                try {
                    blocked.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new java.util.concurrent.CancellationException("cancelled");
                }
                return AgentToolExecutionResult.read("{}", "done", java.util.List.of());
            }
        };
        when(registry.required("prepare_carts")).thenReturn(tool);
        when(json.validateArguments("{}")).thenReturn("{}");
        RecordAgentUserActionCommand command = new RecordAgentUserActionCommand(
                UUID.randomUUID(), UUID.randomUUID(), "prepare_carts", "{}", "stable-key", "Prepare cart"
        );
        when(persistence.reserve(command, "{}", "v1"))
                .thenReturn(new AgentUserActionReservation(actionId, true, null));
        AgentUserActionService service = new AgentUserActionService(
                registry,
                persistence,
                json,
                schema,
                mock(AgentMetrics.class),
                properties()
        );

        try {
            assertThatThrownBy(() -> service.perform(command))
                    .isInstanceOfSatisfying(AgentException.class, exception -> {
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AGENT_ACTION_UNCERTAIN);
                        assertThat(exception.getStatus().value()).isEqualTo(408);
                    });
            verify(persistence).fail(
                    eq(actionId),
                    eq(AgentUserActionStatus.UNCERTAIN),
                    any(String.class)
            );
        } finally {
            service.shutdown();
            blocked.countDown();
        }
    }

    @Test
    void upstreamMutationFailureRemainsUncertainAndRetryableWithTheSameKey() {
        UUID actionId = UUID.randomUUID();
        AgentToolRegistry registry = mock(AgentToolRegistry.class);
        AgentUserActionPersistenceService persistence = mock(AgentUserActionPersistenceService.class);
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentToolSchemaValidator schema = mock(AgentToolSchemaValidator.class);
        AgentTool tool = new AgentTool() {
            @Override
            public AgentToolDescriptor descriptor() {
                return new AgentToolDescriptor(
                        "update_checkout",
                        "Update checkout",
                        "{\"type\":\"object\"}",
                        "v1",
                        AgentToolRisk.REVERSIBLE_MUTATION
                );
            }

            @Override
            public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
                throw new AgentException(
                        HttpStatus.BAD_GATEWAY,
                        ApiErrorCode.UPSTREAM_SERVICE_ERROR,
                        "The provider response was not confirmed."
                );
            }
        };
        when(registry.required("update_checkout")).thenReturn(tool);
        when(json.validateArguments("{}")).thenReturn("{}");
        RecordAgentUserActionCommand command = new RecordAgentUserActionCommand(
                UUID.randomUUID(), UUID.randomUUID(), "update_checkout", "{}", "stable-key", "Update checkout"
        );
        when(persistence.reserve(command, "{}", "v1"))
                .thenReturn(new AgentUserActionReservation(actionId, true, null));
        AgentUserActionService service = new AgentUserActionService(
                registry,
                persistence,
                json,
                schema,
                mock(AgentMetrics.class),
                properties()
        );

        try {
            assertThatThrownBy(() -> service.perform(command))
                    .isInstanceOfSatisfying(AgentException.class, exception -> {
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AGENT_ACTION_UNCERTAIN);
                        assertThat(exception.getStatus().value()).isEqualTo(502);
                    });
            verify(persistence).fail(
                    eq(actionId),
                    eq(AgentUserActionStatus.UNCERTAIN),
                    any(String.class)
            );
        } finally {
            service.shutdown();
        }
    }

    private AgentProperties properties() {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, 24000, 2, Duration.ofMinutes(2), Duration.ofSeconds(30),
                Duration.ofMillis(10), Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }
}
