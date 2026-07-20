package com.meant.api.module.agent.service;

import static com.meant.api.module.agent.support.ScriptedAgentModelGateway.failure;
import static com.meant.api.module.agent.support.ScriptedAgentModelGateway.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.service.dto.AgentExecutedToolCall;
import com.meant.api.module.agent.service.dto.AgentModelContext;
import com.meant.api.module.agent.service.dto.AgentModelMessage;
import com.meant.api.module.agent.service.dto.AgentModelResponse;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentModelToolResult;
import com.meant.api.module.agent.service.dto.AgentModelUsage;
import com.meant.api.module.agent.service.dto.AgentProductClarification;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import com.meant.api.module.agent.support.ScriptedAgentModelGateway;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentRunCoordinatorTest {

    private AgentRunCoordinator coordinator;

    @AfterEach
    void shutDownExecutors() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    void executesSequentialToolRoundsAndPersistsTheGroundedFinalMessage() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AgentModelToolCall search = new AgentModelToolCall("call-1", "search_catalog", "{\"query\":\"shoes\"}");
        AgentModelToolCall detail = new AgentModelToolCall("call-2", "get_product", "{\"canonicalProductKey\":\"p1\"}");
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(search))),
                response(model("", List.of(detail))),
                response(model("Here are two grounded choices.", List.of()), "Here are ", "two grounded choices.")
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        when(fixture.toolExecutor().execute(any(AgentToolExecutionContext.class), any()))
                .thenAnswer(invocation -> {
                    AgentModelToolCall call = invocation.getArgument(1);
                    return new AgentExecutedToolCall(
                            new AgentModelToolResult(call.id(), call.name(), "{\"ok\":true}"),
                            true
                    );
                });

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "Here are two grounded choices.",
                false
        );
        ArgumentCaptor<AgentToolExecutionContext> executionContexts =
                ArgumentCaptor.forClass(AgentToolExecutionContext.class);
        verify(fixture.toolExecutor(), timeout(3000).times(2)).execute(executionContexts.capture(), any());
        assertThat(executionContexts.getAllValues())
                .extracting(AgentToolExecutionContext::buyerIp)
                .containsOnly("203.0.113.42");
        assertThat(model.requests()).hasSize(3);
        assertThat(model.requests().get(1).messages().getLast().toolResults())
                .singleElement()
                .extracting(AgentModelToolResult::toolCallId)
                .isEqualTo("call-1");
    }

    @Test
    void explicitVisibleProductComparisonRequiresTheTypedComparisonToolBeforeProse() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AgentModelToolCall comparison = new AgentModelToolCall(
                "call-compare",
                "compare_products",
                "{\"canonicalProductKeys\":[\"product-1\",\"product-2\"]}"
        );
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(comparison))),
                response(model("The first product is the stronger match.", List.of()))
        ));
        AgentModelContext context = new AgentModelContext(
                List.of(AgentModelMessage.user("compare first two products")),
                "compare first two products",
                new AgentVisibleProductContext(UUID.randomUUID(), List.of(
                        new AgentVisibleProductReference(1, 1, "product-1", "offer-1", "First product"),
                        new AgentVisibleProductReference(2, 2, "product-2", "offer-2", "Second product")
                ))
        );
        Fixture fixture = fixture(runId, conversationId, model, false, context);
        when(fixture.toolExecutor().execute(any(), any()))
                .thenReturn(new AgentExecutedToolCall(
                        new AgentModelToolResult("call-compare", "compare_products", "{\"products\":[]}"),
                        true
                ));

        coordinator.schedule(runId);

        verify(fixture.toolExecutor(), timeout(3000)).execute(
                any(),
                org.mockito.ArgumentMatchers.argThat(call -> "compare_products".equals(call.name()))
        );
        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "The first product is the stronger match.",
                false
        );
        assertThat(model.requests()).extracting(request -> request.requiredToolName())
                .containsExactly("compare_products", null);
    }

    @Test
    void fallsBackOnlyBeforeAnyPrimaryModelOutput() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                failure(new IllegalStateException("unsupported model")),
                response(model("Recovered with the fallback.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "Recovered with the fallback.",
                false
        );
        assertThat(model.requests()).extracting(request -> request.model())
                .containsExactly("primary-model", "fallback-model");
    }

    @Test
    void emptyPrimaryModelResponseUsesFallbackInsteadOfTheGenericRecoveryMessage() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of())),
                response(model("I found a grounded option with the fallback.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "I found a grounded option with the fallback.",
                false
        );
        assertThat(model.requests()).extracting(request -> request.model())
                .containsExactly("primary-model", "fallback-model");
    }

    @Test
    void repeatedIdenticalToolCallsTerminateBeforeASecondMutationStarts() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AgentModelToolCall mutation = new AgentModelToolCall(
                "call-1",
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-1\"}]}"
        );
        AgentModelToolCall repeated = new AgentModelToolCall(
                "call-2",
                "prepare_carts",
                mutation.argumentsJson()
        );
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(mutation))),
                response(model("", List.of(repeated)))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        when(fixture.toolExecutor().execute(any(), any()))
                .thenReturn(new AgentExecutedToolCall(
                        new AgentModelToolResult("call-1", "prepare_carts", "{\"carts\":[]}"),
                        true
                ));

        coordinator.schedule(runId);

        verify(fixture.runService(), timeout(3000)).failOwnedExecution(
                runId,
                fixture.executionOwner(),
                "repeated_tool_call",
                "I stopped a repeated action loop before changing anything else."
        );
        verify(fixture.toolExecutor(), timeout(3000)).execute(any(), any());
        verify(fixture.toolExecutor(), never()).execute(any(),
                org.mockito.ArgumentMatchers.argThat(call -> "call-2".equals(call.id())));
    }

    @Test
    void cancellationStopsBeforeAnyModelOrToolStarts() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("This must not run.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, true);

        coordinator.schedule(runId);

        verify(fixture.runService(), timeout(3000)).cancelOwnedExecution(runId, fixture.executionOwner());
        assertThat(model.requests()).isEmpty();
        verify(fixture.toolExecutor(), never()).execute(any(), any());
    }

    @Test
    void cancellationFromThePrimaryModelNeverStartsTheFallbackModel() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                failure(new CancellationException("stopped")),
                response(model("This fallback must not run.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);

        coordinator.schedule(runId);

        verify(fixture.runService(), timeout(3000)).cancelOwnedExecution(runId, fixture.executionOwner());
        assertThat(model.requests()).extracting(request -> request.model())
                .containsExactly("primary-model");
        verify(fixture.toolExecutor(), never()).execute(any(), any());
    }

    @Test
    void cancellationAfterTheLastModelChunkWinsBeforeTerminalCompletion() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("A final answer that must not be committed.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        when(fixture.runService().cancellationRequested(runId, fixture.executionOwner()))
                .thenReturn(false, false, true, true);

        coordinator.schedule(runId);

        verify(fixture.runService(), timeout(3000)).cancelOwnedExecution(runId, fixture.executionOwner());
        verify(fixture.messageLedger(), never()).appendTerminalAssistant(any(), any(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void claimContentionDoesNotHotRescheduleTheSameQueuedRun() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("This must not execute.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        when(fixture.runService().claim(runId)).thenReturn(Optional.empty());

        coordinator.schedule(runId);

        verify(fixture.runService(), after(300).times(1)).claim(runId);
        assertThat(model.requests()).isEmpty();
        verify(fixture.toolExecutor(), never()).execute(any(), any());
    }

    @Test
    void unresolvedProductIntentWaitsBeforeTheModelOrAnyToolStarts() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("This model response must not be needed.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        AgentProductClarification clarification = clarification();
        when(fixture.productClarificationService().unresolvedIntent(any()))
                .thenReturn(Optional.of(clarification));
        when(fixture.productClarificationService().question(clarification))
                .thenReturn("Which product should I add to your cart?\n1. Blue cap\n2. Red cap");
        when(fixture.productClarificationContextService().serialize(clarification))
                .thenReturn("{\"pendingProductClarification\":true}");

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "Which product should I add to your cart?\n1. Blue cap\n2. Red cap",
                "{\"pendingProductClarification\":true}",
                true
        );
        assertThat(model.requests()).isEmpty();
        verify(fixture.toolExecutor(), never()).execute(any(), any());
    }

    @Test
    void completedMutationIsNotReclassifiedAsAnUnresolvedProductIntent() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AgentModelToolCall mutation = new AgentModelToolCall(
                "call-1",
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-red\"}]}"
        );
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(mutation))),
                response(model("I added the selected product to your cart.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        AgentProductClarification staleClarification = clarification();
        when(fixture.productClarificationService().unresolvedIntent(any()))
                .thenReturn(Optional.empty(), Optional.of(staleClarification));
        when(fixture.toolExecutor().execute(any(), any()))
                .thenReturn(new AgentExecutedToolCall(
                        new AgentModelToolResult("call-1", "prepare_carts", "{\"carts\":[]}"),
                        true
                ));

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "I added the selected product to your cart.",
                false
        );
        verify(fixture.productClarificationService(), timeout(3000).times(1)).unresolvedIntent(any());
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void readOnlyRoundStillRechecksForAnUnresolvedProductIntent() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AgentModelToolCall read = new AgentModelToolCall(
                "call-1", "search_catalog", "{\"query\":\"caps\"}"
        );
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(read)))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        AgentProductClarification clarification = clarification();
        when(fixture.productClarificationService().unresolvedIntent(any()))
                .thenReturn(Optional.empty(), Optional.of(clarification));
        when(fixture.productClarificationService().question(clarification))
                .thenReturn("Which product should I add to your cart?\n1. Blue cap\n2. Red cap");
        when(fixture.productClarificationContextService().serialize(clarification))
                .thenReturn("{\"pendingProductClarification\":true}");
        when(fixture.toolExecutor().execute(any(), any()))
                .thenReturn(new AgentExecutedToolCall(
                        new AgentModelToolResult("call-1", "search_catalog", "{\"products\":[]}"),
                        true
                ));

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "Which product should I add to your cart?\n1. Blue cap\n2. Red cap",
                "{\"pendingProductClarification\":true}",
                true
        );
        verify(fixture.productClarificationService(), timeout(3000).times(2)).unresolvedIntent(any());
        verify(fixture.toolExecutor(), timeout(3000)).execute(any(), any());
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void productPreflightStopsEveryCallInTheResponseBeforeExecution() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AgentModelToolCall read = new AgentModelToolCall(
                "call-1", "search_catalog", "{\"query\":\"caps\"}");
        AgentModelToolCall mutation = new AgentModelToolCall(
                "call-2", "prepare_carts", "{\"offers\":[{\"offerKey\":\"offer-red\"}]}"
        );
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(read, mutation)))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        AgentProductClarification clarification = clarification();
        when(fixture.productClarificationService().preflight(
                any(AgentToolExecutionContext.class), any()))
                .thenReturn(Optional.of(clarification));
        when(fixture.productClarificationService().question(clarification))
                .thenReturn("Which product should I add to your cart?\n1. Blue cap\n2. Red cap");
        when(fixture.productClarificationContextService().serialize(clarification))
                .thenReturn("{\"pendingProductClarification\":true}");

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "Which product should I add to your cart?\n1. Blue cap\n2. Red cap",
                "{\"pendingProductClarification\":true}",
                true
        );
        verify(fixture.toolExecutor(), never()).execute(any(), any());
        assertThat(model.requests()).hasSize(1);
    }

    private Fixture fixture(
            UUID runId,
            UUID conversationId,
            ScriptedAgentModelGateway model,
            boolean cancelled
    ) {
        return fixture(
                runId,
                conversationId,
                model,
                cancelled,
                new AgentModelContext(List.of(AgentModelMessage.user("Find shoes")), "Find shoes")
        );
    }

    private Fixture fixture(
            UUID runId,
            UUID conversationId,
            ScriptedAgentModelGateway model,
            boolean cancelled,
            AgentModelContext modelContext
    ) {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        UUID executionOwner = UUID.randomUUID();
        AgentRunService runService = mock(AgentRunService.class);
        AgentContextAssembler contextAssembler = mock(AgentContextAssembler.class);
        AgentToolRegistry registry = mock(AgentToolRegistry.class);
        AgentToolAuthorizationPolicy authorizationPolicy = mock(AgentToolAuthorizationPolicy.class);
        AgentToolCallExecutor toolExecutor = mock(AgentToolCallExecutor.class);
        AgentProductClarificationService productClarificationService =
                mock(AgentProductClarificationService.class);
        AgentProductClarificationContextService productClarificationContextService =
                mock(AgentProductClarificationContextService.class);
        AgentMessageLedgerService messageLedger = mock(AgentMessageLedgerService.class);
        AgentJsonSupport jsonSupport = mock(AgentJsonSupport.class);
        AgentTool tool = mock(AgentTool.class);
        AgentToolDescriptor descriptor = new AgentToolDescriptor(
                "search_catalog",
                "Search",
                "{\"type\":\"object\",\"additionalProperties\":false}",
                "1",
                AgentToolRisk.READ
        );
        AgentToolDescriptor mutationDescriptor = new AgentToolDescriptor(
                "prepare_carts",
                "Prepare carts",
                "{\"type\":\"object\",\"additionalProperties\":false}",
                "1",
                AgentToolRisk.REVERSIBLE_MUTATION
        );
        AgentToolDescriptor comparisonDescriptor = new AgentToolDescriptor(
                "compare_products",
                "Compare products",
                "{\"type\":\"object\",\"additionalProperties\":false}",
                "1",
                AgentToolRisk.READ
        );
        AgentRun run = AgentRun.builder()
                .id(runId)
                .conversationId(conversationId)
                .userId(UUID.randomUUID())
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.RUNNING)
                .model("primary-model")
                .promptVersion("test-v1")
                .buyerIp("203.0.113.42")
                .createdAt(Instant.now())
                .build();
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        when(runs.findFirstByConversationIdAndStatusInOrderByCreatedAtAsc(any(), any()))
                .thenReturn(Optional.empty());
        when(runService.claim(runId)).thenReturn(Optional.of(executionOwner));
        when(runService.cancellationRequested(runId, executionOwner)).thenReturn(cancelled);
        when(contextAssembler.assemble(runId)).thenReturn(modelContext);
        when(registry.descriptors()).thenReturn(List.of(descriptor, mutationDescriptor, comparisonDescriptor));
        when(authorizationPolicy.available(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(jsonSupport.canonicalizeOrOriginal(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tool.descriptor()).thenReturn(descriptor);
        when(registry.required(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            AgentTool selected = mock(AgentTool.class);
            when(selected.descriptor()).thenReturn(switch (name) {
                case "prepare_carts" -> mutationDescriptor;
                case "compare_products" -> comparisonDescriptor;
                default -> descriptor;
            });
            return selected;
        });
        coordinator = new AgentRunCoordinator(
                runs,
                runService,
                contextAssembler,
                registry,
                authorizationPolicy,
                toolExecutor,
                productClarificationService,
                productClarificationContextService,
                messageLedger,
                model,
                mock(AgentMetrics.class),
                jsonSupport,
                properties()
        );
        return new Fixture(
                runService,
                toolExecutor,
                productClarificationService,
                productClarificationContextService,
                messageLedger,
                executionOwner
        );
    }

    private AgentProductClarification clarification() {
        return new AgentProductClarification(
                "prepare_carts",
                "Add the third blue one to my cart",
                List.of(
                        new AgentVisibleProductReference(1, 1, "blue-cap", "offer-blue", "Blue cap"),
                        new AgentVisibleProductReference(2, 2, "red-cap", "offer-red", "Red cap")
                )
        );
    }

    private AgentModelResponse model(String text, List<AgentModelToolCall> calls) {
        return new AgentModelResponse(
                text,
                calls,
                new AgentModelUsage(10L, 5L),
                calls.isEmpty() ? "stop" : "tool_calls",
                "test-model"
        );
    }

    private AgentProperties properties() {
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
                64000,
                24000,
                2,
                Duration.ofSeconds(10),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofMillis(10),
                128,
                Duration.ofDays(1),
                Duration.ofMinutes(5)
        );
    }

    private record Fixture(
            AgentRunService runService,
            AgentToolCallExecutor toolExecutor,
            AgentProductClarificationService productClarificationService,
            AgentProductClarificationContextService productClarificationContextService,
            AgentMessageLedgerService messageLedger,
            UUID executionOwner
    ) {
    }
}
