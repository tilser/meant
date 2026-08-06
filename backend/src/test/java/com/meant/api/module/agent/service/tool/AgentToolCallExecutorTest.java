package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.common.service.UserMutationExecutionLane;
import com.meant.api.module.agent.constant.AgentMutationAdmission;
import com.meant.api.module.agent.constant.AgentToolInvocationStatus;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentMutationExecutionLane;
import com.meant.api.module.agent.service.ReferenceIntegrityPolicy;
import com.meant.api.module.agent.service.AgentRunService;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.AgentToolInvocationReservation;
import com.meant.api.module.user.exception.UnsupportedProductSearchCurrencyException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentToolCallExecutorTest {

    private AgentToolCallExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void mutationTimeoutIsUncertainAndTheProviderReceivesTheReservedInvocationUuid() {
        AgentTool tool = mock(AgentTool.class);
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "prepare_carts",
                "Prepare carts",
                "{\"type\":\"object\"}",
                "v1",
                AgentToolRisk.REVERSIBLE_MUTATION
        );
        when(tool.descriptor()).thenReturn(descriptor);
        AtomicReference<AgentToolExecutionContext> executedContext = new AtomicReference<>();
        when(tool.execute(any(), anyString())).thenAnswer(invocation -> {
            executedContext.set(invocation.getArgument(0));
            try {
                Thread.sleep(Duration.ofSeconds(10));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CancellationException("cancelled");
            }
            throw new AssertionError("The test tool should have been cancelled");
        });
        AgentToolAuthorizationPolicy authorizationPolicy = mock(AgentToolAuthorizationPolicy.class);
        when(authorizationPolicy.authorized(any(), eq(descriptor))).thenReturn(true);
        when(authorizationPolicy.authorizedInvocation(any(), eq(descriptor), anyString())).thenReturn(true);
        AgentToolInvocationService invocationService = mock(AgentToolInvocationService.class);
        UUID invocationId = UUID.randomUUID();
        when(invocationService.reserve(any(), any(), eq(descriptor), anyString(), anyString(), isNull()))
                .thenReturn(new AgentToolInvocationReservation(
                        invocationId, true, null, List.of(), false));
        AgentRunService runService = mock(AgentRunService.class);
        AgentJsonSupport jsonSupport = mock(AgentJsonSupport.class);
        when(jsonSupport.validateArguments(anyString())).thenReturn("{}");
        when(jsonSupport.write(any())).thenReturn("{\"success\":false}");
        executor = new AgentToolCallExecutor(
                new AgentToolRegistry(List.of(tool)),
                authorizationPolicy,
                invocationService,
                runService,
                jsonSupport,
                mock(AgentToolSchemaValidator.class),
                acceptingReferences(),
                new AgentMutationExecutionLane(new UserMutationExecutionLane()),
                properties()
        );
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "add this to cart");

        var result = executor.execute(context, new AgentModelToolCall("call-1", "prepare_carts", "{}"));

        assertThat(result.successful()).isFalse();
        assertThat(executedContext.get().idempotencyKey()).isEqualTo(invocationId);
        verify(invocationService).fail(
                eq(context.runId()),
                eq(invocationId),
                eq("call-1"),
                eq("prepare_carts"),
                eq(AgentToolInvocationStatus.UNCERTAIN),
                eq("timeout"),
                eq("The tool timed out. Try again."),
                anyLong(),
                isNull()
        );
    }

    @Test
    void invalidArgumentsTellTheModelHowToCorrectItsRetry() {
        AgentTool tool = mock(AgentTool.class);
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "search_catalog",
                "Search catalog",
                "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"query\"],"
                        + "\"properties\":{\"query\":{\"type\":\"string\"}}}",
                "v1",
                AgentToolRisk.READ
        );
        when(tool.descriptor()).thenReturn(descriptor);
        AgentToolAuthorizationPolicy authorizationPolicy = mock(AgentToolAuthorizationPolicy.class);
        when(authorizationPolicy.authorized(any(), eq(descriptor))).thenReturn(true);
        AgentToolInvocationService invocationService = mock(AgentToolInvocationService.class);
        AgentRunService runService = mock(AgentRunService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentJsonSupport jsonSupport = new AgentJsonSupport(objectMapper, properties());
        executor = new AgentToolCallExecutor(
                new AgentToolRegistry(List.of(tool)),
                authorizationPolicy,
                invocationService,
                runService,
                jsonSupport,
                new AgentToolSchemaValidator(objectMapper),
                acceptingReferences(),
                new AgentMutationExecutionLane(new UserMutationExecutionLane()),
                properties()
        );
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "find shoes");

        var result = executor.execute(
                context,
                new AgentModelToolCall("call-1", "search_catalog", "{\"unexpected\":true}")
        );

        assertThat(result.successful()).isFalse();
        assertThat(result.modelResult().resultJson())
                .contains("\"code\":\"invalid_arguments\"")
                .contains("$.query is required.")
                .contains("\"retryable\":true");
    }

    @Test
    void readToolDomainErrorsPreserveTheirSafeMessageWithoutInvitingARetry() {
        AgentTool tool = mock(AgentTool.class);
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "search_catalog",
                "Search catalog",
                "{\"type\":\"object\"}",
                "v1",
                AgentToolRisk.READ
        );
        when(tool.descriptor()).thenReturn(descriptor);
        when(tool.execute(any(), anyString()))
                .thenThrow(UnsupportedProductSearchCurrencyException.mixed("USD"));
        AgentToolAuthorizationPolicy authorizationPolicy = mock(AgentToolAuthorizationPolicy.class);
        when(authorizationPolicy.authorized(any(), eq(descriptor))).thenReturn(true);
        AgentToolInvocationService invocationService = mock(AgentToolInvocationService.class);
        UUID invocationId = UUID.randomUUID();
        when(invocationService.reserve(any(), any(), eq(descriptor), anyString(), anyString(), isNull()))
                .thenReturn(new AgentToolInvocationReservation(
                        invocationId, true, null, List.of(), false));
        AgentProperties stableExecutionProperties = properties(Duration.ofSeconds(5));
        ObjectMapper objectMapper = new ObjectMapper();
        AgentJsonSupport jsonSupport = new AgentJsonSupport(objectMapper, stableExecutionProperties);
        executor = new AgentToolCallExecutor(
                new AgentToolRegistry(List.of(tool)),
                authorizationPolicy,
                invocationService,
                mock(AgentRunService.class),
                jsonSupport,
                new AgentToolSchemaValidator(objectMapper),
                acceptingReferences(),
                new AgentMutationExecutionLane(new UserMutationExecutionLane()),
                stableExecutionProperties
        );
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "find shoes");

        var result = executor.execute(
                context,
                new AgentModelToolCall("call-currency", "search_catalog", "{}")
        );

        assertThat(result.successful()).isFalse();
        assertThat(result.modelResult().resultJson())
                .contains("\"code\":\"domain_error\"")
                .contains("Price amounts use different currencies. Use only one currency in a search.")
                .contains("\"retryable\":false");
        verify(invocationService).fail(
                eq(context.runId()),
                eq(invocationId),
                eq("call-currency"),
                eq("search_catalog"),
                eq(AgentToolInvocationStatus.FAILED),
                eq("domain_error"),
                eq("Price amounts use different currencies. Use only one currency in a search."),
                anyLong(),
                isNull()
        );
    }

    @Test
    void hallucinatedReferenceReturnsStructuredToolErrorWithoutExecutingTheMutation() {
        AgentTool tool = mock(AgentTool.class);
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "prepare_carts",
                "Prepare carts",
                "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"offerKey\"],"
                        + "\"properties\":{\"offerKey\":{\"type\":\"string\"}}}",
                "v1",
                AgentToolRisk.REVERSIBLE_MUTATION
        );
        when(tool.descriptor()).thenReturn(descriptor);
        AgentToolAuthorizationPolicy authorizationPolicy = mock(AgentToolAuthorizationPolicy.class);
        when(authorizationPolicy.authorized(any(), eq(descriptor))).thenReturn(true);
        AgentToolInvocationService invocationService = mock(AgentToolInvocationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentJsonSupport jsonSupport = new AgentJsonSupport(objectMapper, properties());
        ReferenceIntegrityPolicy references = mock(ReferenceIntegrityPolicy.class);
        when(references.validate(any(), eq("prepare_carts"), anyString()))
                .thenReturn(ReferenceIntegrityPolicy.Validation.rejected(
                        "reference_not_found", "offerKey"));
        executor = new AgentToolCallExecutor(
                new AgentToolRegistry(List.of(tool)),
                authorizationPolicy,
                invocationService,
                mock(AgentRunService.class),
                jsonSupport,
                new AgentToolSchemaValidator(objectMapper),
                references,
                new AgentMutationExecutionLane(new UserMutationExecutionLane()),
                properties()
        );
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "add it");
        AgentModelToolCall call = new AgentModelToolCall(
                "call-reference", "prepare_carts", "{\"offerKey\":\"invented\"}");

        var result = executor.execute(context, call);

        assertThat(result.successful()).isFalse();
        assertThat(result.modelResult().resultJson())
                .isEqualTo("{\"error\":\"reference_not_found\",\"field\":\"offerKey\"}");
        verify(invocationService).rejectUnauthorized(
                context.runId(), call, descriptor, context.executionOwner());
        verify(tool, never()).execute(any(), anyString());
    }

    @Test
    void propagatesAWaitingForUserOutcomeFromTheExecutedTool() {
        AgentTool tool = mock(AgentTool.class);
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "search_catalog",
                "Search catalog",
                "{\"type\":\"object\"}",
                "v1",
                AgentToolRisk.READ
        );
        String question = "Where should the order ship?";
        when(tool.descriptor()).thenReturn(descriptor);
        when(tool.execute(any(), anyString()))
                .thenReturn(AgentToolExecutionResult.waitingForUser(
                        "{\"qualificationQuestion\":\"Where should the order ship?\"}",
                        question
                ));
        AgentToolAuthorizationPolicy authorizationPolicy = mock(AgentToolAuthorizationPolicy.class);
        when(authorizationPolicy.authorized(any(), eq(descriptor))).thenReturn(true);
        when(authorizationPolicy.authorizedInvocation(any(), eq(descriptor), anyString())).thenReturn(true);
        AgentToolInvocationService invocationService = mock(AgentToolInvocationService.class);
        when(invocationService.reserve(any(), any(), eq(descriptor), anyString(), anyString(), isNull()))
                .thenReturn(new AgentToolInvocationReservation(
                        UUID.randomUUID(), true, null, List.of(), false));
        AgentProperties stableExecutionProperties = properties(Duration.ofSeconds(5));
        ObjectMapper objectMapper = new ObjectMapper();
        AgentJsonSupport jsonSupport = new AgentJsonSupport(objectMapper, stableExecutionProperties);
        executor = new AgentToolCallExecutor(
                new AgentToolRegistry(List.of(tool)),
                authorizationPolicy,
                invocationService,
                mock(AgentRunService.class),
                jsonSupport,
                new AgentToolSchemaValidator(objectMapper),
                acceptingReferences(),
                new AgentMutationExecutionLane(new UserMutationExecutionLane()),
                stableExecutionProperties
        );
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "black jacket");

        var result = executor.execute(context, new AgentModelToolCall("call-search", "search_catalog", "{}"));

        assertThat(result.successful()).isTrue();
        assertThat(result.waitingForUserMessage()).isEqualTo(question);
    }

    @Test
    void propagatesAWaitingForUserOutcomeFromAnIdempotentReplay() {
        AgentTool tool = mock(AgentTool.class);
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "search_catalog",
                "Search catalog",
                "{\"type\":\"object\"}",
                "v1",
                AgentToolRisk.READ
        );
        String question = "Where should the order ship?";
        when(tool.descriptor()).thenReturn(descriptor);
        AgentToolAuthorizationPolicy authorizationPolicy = mock(AgentToolAuthorizationPolicy.class);
        when(authorizationPolicy.authorized(any(), eq(descriptor))).thenReturn(true);
        when(authorizationPolicy.authorizedInvocation(any(), eq(descriptor), anyString())).thenReturn(true);
        AgentToolInvocationService invocationService = mock(AgentToolInvocationService.class);
        when(invocationService.reserve(any(), any(), eq(descriptor), anyString(), anyString(), isNull()))
                .thenReturn(new AgentToolInvocationReservation(
                        UUID.randomUUID(),
                        false,
                        "{\"qualificationQuestion\":\"" + question + "\"}",
                        List.of(),
                        false,
                        question
                ));
        ObjectMapper objectMapper = new ObjectMapper();
        AgentJsonSupport jsonSupport = new AgentJsonSupport(objectMapper, properties());
        executor = new AgentToolCallExecutor(
                new AgentToolRegistry(List.of(tool)),
                authorizationPolicy,
                invocationService,
                mock(AgentRunService.class),
                jsonSupport,
                new AgentToolSchemaValidator(objectMapper),
                acceptingReferences(),
                new AgentMutationExecutionLane(new UserMutationExecutionLane()),
                properties()
        );
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "black jacket");

        var result = executor.execute(context, new AgentModelToolCall("call-search", "search_catalog", "{}"));

        assertThat(result.successful()).isTrue();
        assertThat(result.waitingForUserMessage()).isEqualTo(question);
        verify(tool, never()).execute(any(), anyString());
    }

    @Test
    void waitingNewMutationTimesOutAsFailedWithoutExecuting() throws Exception {
        assertWaitingTimeout(false, AgentToolInvocationStatus.FAILED);
    }

    @Test
    void waitingReconciliationRetryPreservesTheEarlierUncertainOutcome() throws Exception {
        assertWaitingTimeout(true, AgentToolInvocationStatus.UNCERTAIN);
    }

    private void assertWaitingTimeout(
            boolean reconciliationRetry,
            AgentToolInvocationStatus expectedStatus
    ) throws Exception {
        UUID userId = UUID.randomUUID();
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

        AgentTool tool = mock(AgentTool.class);
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "prepare_carts",
                "Prepare carts",
                "{\"type\":\"object\"}",
                "v1",
                AgentToolRisk.REVERSIBLE_MUTATION
        );
        when(tool.descriptor()).thenReturn(descriptor);
        AtomicBoolean executed = new AtomicBoolean();
        when(tool.execute(any(), anyString())).thenAnswer(invocation -> {
            executed.set(true);
            throw new AssertionError("Waiting mutation must not execute");
        });
        AgentToolAuthorizationPolicy authorizationPolicy = mock(AgentToolAuthorizationPolicy.class);
        when(authorizationPolicy.authorized(any(), eq(descriptor))).thenReturn(true);
        when(authorizationPolicy.authorizedInvocation(any(), eq(descriptor), anyString())).thenReturn(true);
        AgentToolInvocationService invocationService = mock(AgentToolInvocationService.class);
        UUID invocationId = UUID.randomUUID();
        when(invocationService.reserve(any(), any(), eq(descriptor), anyString(), anyString(), isNull()))
                .thenReturn(new AgentToolInvocationReservation(
                        invocationId,
                        true,
                        null,
                        List.of(),
                        reconciliationRetry
                ));
        AgentJsonSupport jsonSupport = mock(AgentJsonSupport.class);
        when(jsonSupport.validateArguments(anyString())).thenReturn("{}");
        when(jsonSupport.write(any())).thenReturn("{\"success\":false}");
        executor = new AgentToolCallExecutor(
                new AgentToolRegistry(List.of(tool)),
                authorizationPolicy,
                invocationService,
                mock(AgentRunService.class),
                jsonSupport,
                mock(AgentToolSchemaValidator.class),
                acceptingReferences(),
                new AgentMutationExecutionLane(sharedLane),
                properties()
        );
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "add this to cart"
        );

        try {
            var result = executor.execute(context, new AgentModelToolCall("call-wait", "prepare_carts", "{}"));

            assertThat(result.successful()).isFalse();
            verify(invocationService).fail(
                    eq(context.runId()),
                    eq(invocationId),
                    eq("call-wait"),
                    eq("prepare_carts"),
                    eq(expectedStatus),
                    eq(reconciliationRetry
                            ? "timeout"
                            : AgentMutationAdmission.FAILURE_CLASSIFICATION),
                    anyString(),
                    anyLong(),
                    isNull()
            );
            assertThat(executed).isFalse();
        } finally {
            releaseLane.countDown();
            holder.join(1000);
        }
        assertThat(executed).isFalse();
    }

    private AgentProperties properties() {
        return properties(Duration.ofMillis(20));
    }

    private AgentProperties properties(Duration toolDeadline) {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, 24000, 2, Duration.ofMinutes(2), Duration.ofSeconds(30),
                toolDeadline, Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }

    private ReferenceIntegrityPolicy acceptingReferences() {
        ReferenceIntegrityPolicy policy = mock(ReferenceIntegrityPolicy.class);
        when(policy.validate(any(), anyString(), anyString()))
                .thenReturn(ReferenceIntegrityPolicy.Validation.ok());
        return policy;
    }
}
