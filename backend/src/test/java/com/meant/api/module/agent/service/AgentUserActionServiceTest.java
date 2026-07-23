package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.service.UserMutationExecutionLane;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.constant.AgentUserActionStatus;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.command.RecordAgentUserActionCommand;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.AgentUserActionReservation;
import com.meant.api.module.agent.service.tool.AgentTool;
import com.meant.api.module.agent.service.tool.AgentToolRegistry;
import com.meant.api.module.agent.service.tool.AgentToolSchemaValidator;
import com.meant.api.module.user.exception.SelectedOfferResolutionException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AgentUserActionServiceTest {

    @Test
    void timedOutMutationReturnsAnExplicitUncertainCodeSoTheClientReusesItsKey() {
        UUID actionId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        AgentToolRegistry registry = mock(AgentToolRegistry.class);
        AgentUserActionPersistenceService persistence = mock(AgentUserActionPersistenceService.class);
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentToolSchemaValidator schema = mock(AgentToolSchemaValidator.class);
        CountDownLatch blocked = new CountDownLatch(1);
        AtomicReference<String> observedBuyerIp = new AtomicReference<>();
        AtomicReference<UUID> observedMerchantId = new AtomicReference<>();
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
                observedBuyerIp.set(context.buyerIp());
                observedMerchantId.set(context.merchantId());
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
                UUID.randomUUID(), UUID.randomUUID(), "prepare_carts", "{}", "stable-key", "Prepare cart",
                "203.0.113.42"
        );
        when(persistence.reserve(command, "{}", "v1"))
                .thenReturn(new AgentUserActionReservation(actionId, true, null, merchantId));
        AgentUserActionService service = new AgentUserActionService(
                registry,
                persistence,
                json,
                schema,
                mock(AgentMetrics.class),
                new AgentMutationExecutionLane(new UserMutationExecutionLane()),
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
            assertThat(observedBuyerIp.get()).isEqualTo("203.0.113.42");
            assertThat(observedMerchantId.get()).isEqualTo(merchantId);
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
                new AgentMutationExecutionLane(new UserMutationExecutionLane()),
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

    @Test
    void deterministicCommerceRejectionPreservesItsStatusAndSafeMessage() {
        UUID actionId = UUID.randomUUID();
        AgentToolRegistry registry = mock(AgentToolRegistry.class);
        AgentUserActionPersistenceService persistence = mock(AgentUserActionPersistenceService.class);
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentToolSchemaValidator schema = mock(AgentToolSchemaValidator.class);
        SelectedOfferResolutionException rejection =
                SelectedOfferResolutionException.unknownOrExpired();
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
                throw rejection;
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
                new AgentMutationExecutionLane(new UserMutationExecutionLane()),
                properties()
        );

        try {
            assertThatThrownBy(() -> service.perform(command)).isSameAs(rejection);
            verify(persistence).fail(
                    actionId,
                    AgentUserActionStatus.FAILED,
                    rejection.getSafeMessage()
            );
        } finally {
            service.shutdown();
        }
    }

    @Test
    void waitingNewMutationTimesOutAsFailedWithoutExecuting() throws Exception {
        assertWaitingTimeout(false, ApiErrorCode.AGENT_CONFLICT, AgentUserActionStatus.FAILED);
    }

    @Test
    void waitingReconciliationRetryPreservesTheEarlierUncertainOutcome() throws Exception {
        assertWaitingTimeout(
                true,
                ApiErrorCode.AGENT_ACTION_UNCERTAIN,
                AgentUserActionStatus.UNCERTAIN
        );
    }

    private void assertWaitingTimeout(
            boolean reconciliationRetry,
            ApiErrorCode expectedErrorCode,
            AgentUserActionStatus expectedStatus
    ) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        UserMutationExecutionLane sharedLane = new UserMutationExecutionLane();
        CountDownLatch laneEntered = new CountDownLatch(1);
        CountDownLatch releaseLane = new CountDownLatch(1);
        Thread holder = Thread.ofVirtual().start(() -> {
            try {
                sharedLane.execute(userId, () -> {
                    laneEntered.countDown();
                    try {
                        releaseLane.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(laneEntered.await(1, TimeUnit.SECONDS)).isTrue();

        AgentToolRegistry registry = mock(AgentToolRegistry.class);
        AgentUserActionPersistenceService persistence = mock(AgentUserActionPersistenceService.class);
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AtomicBoolean executed = new AtomicBoolean();
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
                executed.set(true);
                return AgentToolExecutionResult.read("{}", "done", java.util.List.of());
            }
        };
        when(registry.required("prepare_carts")).thenReturn(tool);
        when(json.validateArguments("{}")).thenReturn("{}");
        RecordAgentUserActionCommand command = new RecordAgentUserActionCommand(
                userId,
                UUID.randomUUID(),
                "prepare_carts",
                "{}",
                "stable-key",
                "Prepare cart"
        );
        when(persistence.reserve(command, "{}", "v1"))
                .thenReturn(new AgentUserActionReservation(
                        actionId,
                        true,
                        null,
                        null,
                        reconciliationRetry
                ));
        AgentUserActionService service = new AgentUserActionService(
                registry,
                persistence,
                json,
                mock(AgentToolSchemaValidator.class),
                mock(AgentMetrics.class),
                new AgentMutationExecutionLane(sharedLane),
                properties()
        );

        try {
            assertThatThrownBy(() -> service.perform(command))
                    .isInstanceOfSatisfying(AgentException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode));
            verify(persistence).fail(eq(actionId), eq(expectedStatus), any(String.class));
            assertThat(executed).isFalse();
        } finally {
            service.shutdown();
            releaseLane.countDown();
            holder.join(1000);
        }
        assertThat(executed).isFalse();
    }

    private AgentProperties properties() {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, 24000, 2, Duration.ofMinutes(2), Duration.ofSeconds(30),
                Duration.ofMillis(100), Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }
}
