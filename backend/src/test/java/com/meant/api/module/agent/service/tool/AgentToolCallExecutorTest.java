package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentToolInvocationStatus;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentRunService;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolInvocationReservation;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
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

    private AgentProperties properties() {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, 24000, 2, Duration.ofMinutes(2), Duration.ofSeconds(30),
                Duration.ofMillis(20), Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }
}
