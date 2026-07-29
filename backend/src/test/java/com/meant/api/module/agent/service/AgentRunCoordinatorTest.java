package com.meant.api.module.agent.service;

import static com.meant.api.module.agent.support.ScriptedAgentModelGateway.failure;
import static com.meant.api.module.agent.support.ScriptedAgentModelGateway.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.meant.api.module.agent.service.dto.AgentResolvedReadIntent;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import com.meant.api.module.agent.service.port.AgentModelGateway;
import com.meant.api.module.agent.service.tool.AgentTool;
import com.meant.api.module.agent.service.tool.AgentToolAuthorizationPolicy;
import com.meant.api.module.agent.service.tool.AgentToolCallExecutor;
import com.meant.api.module.agent.service.tool.AgentToolRegistry;
import com.meant.api.module.agent.support.ScriptedAgentModelGateway;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

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
        UUID merchantId = UUID.randomUUID();
        AgentModelToolCall search = new AgentModelToolCall("call-1", "search_catalog", "{\"query\":\"shoes\"}");
        AgentModelToolCall detail = new AgentModelToolCall("call-2", "get_product", "{\"canonicalProductKey\":\"p1\"}");
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(search))),
                response(model("", List.of(detail))),
                response(model("Here are two grounded choices.", List.of()), "Here are ", "two grounded choices.")
        ));
        Fixture fixture = fixture(
                runId,
                conversationId,
                model,
                false,
                new AgentModelContext(
                        List.of(AgentModelMessage.user("Find shoes")),
                        "Find shoes",
                        null,
                        null,
                        merchantId
                )
        );
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
        assertThat(executionContexts.getAllValues())
                .extracting(AgentToolExecutionContext::userAgent)
                .containsOnly("Meant Browser/1.0");
        assertThat(executionContexts.getAllValues())
                .extracting(AgentToolExecutionContext::merchantId)
                .containsOnly(merchantId);
        assertThat(model.requests()).hasSize(3);
        assertThat(model.requests().get(1).messages().getLast().toolResults())
                .singleElement()
                .extracting(AgentModelToolResult::toolCallId)
                .isEqualTo("call-1");
    }

    @Test
    void qualificationQuestionStopsTheRunAndWaitsForTheUserWithoutAnotherModelTurn() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String question = "What country or postal code should the order ship to? "
                + "You can also say “I don’t care” if location should not filter the search.";
        AgentModelToolCall search =
                new AgentModelToolCall("call-search", "search_catalog", "{\"query\":\"black jacket\"}");
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(search))),
                response(model("This second turn must not run.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        when(fixture.toolExecutor().execute(any(), any())).thenReturn(new AgentExecutedToolCall(
                new AgentModelToolResult(
                        "call-search",
                        "search_catalog",
                        "{\"qualificationQuestion\":\"" + question + "\"}"
                ),
                true,
                question
        ));

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                question,
                true
        );
        assertThat(model.requests()).hasSize(1);
        verify(fixture.runService(), never()).failOwnedExecution(
                eq(runId),
                eq(fixture.executionOwner()),
                eq("repeated_tool_call"),
                anyString()
        );
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
    void clearInitialProductSearchStartsServerQualificationWithoutCallingTheOuterModel() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String turn = "cool running shoes";
        String question = "What shoe size do you need?";
        AgentModelToolCall searchCall = new AgentModelToolCall(
                null,
                "search_catalog",
                "{\"query\":\"cool running shoes\"}"
        );
        AgentResolvedReadIntent resolved = new AgentResolvedReadIntent(searchCall, turn);
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("This model response must not be needed.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        when(fixture.contextAssembler().assemble(runId)).thenReturn(new AgentModelContext(
                List.of(AgentModelMessage.user(turn)),
                turn,
                null
        ));
        when(fixture.readIntentResolver().resolve(any())).thenReturn(Optional.of(resolved));
        when(fixture.toolExecutor().execute(any(), any())).thenReturn(new AgentExecutedToolCall(
                new AgentModelToolResult(
                        runId + "-0-0",
                        "search_catalog",
                        "{\"qualificationQuestion\":\"" + question + "\"}"
                ),
                true,
                question
        ));

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                question,
                true
        );
        ArgumentCaptor<AgentModelToolCall> callCaptor = ArgumentCaptor.forClass(AgentModelToolCall.class);
        verify(fixture.toolExecutor(), timeout(3000)).execute(any(), callCaptor.capture());
        assertThat(callCaptor.getValue()).isEqualTo(new AgentModelToolCall(
                runId + "-0-0",
                "search_catalog",
                "{\"query\":\"cool running shoes\"}"
        ));
        assertThat(callCaptor.getValue().argumentsJson())
                .doesNotContain("qualificationId", "qualificationUpdatedAt");
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void bareNovelProductSearchCannotTerminateWithOuterModelQualificationProse() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String turn = "ceramic yarn bowl";
        String serverQuestion = "What country should the order ship to?";
        String untrustedModelQuestion = "What kind of bowl would you like?";
        AgentResolvedReadIntent resolved = new AgentResolvedReadIntent(
                new AgentModelToolCall(
                        null,
                        "search_catalog",
                        "{\"query\":\"ceramic yarn bowl\"}"
                ),
                turn
        );
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model(untrustedModelQuestion, List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        when(fixture.contextAssembler().assemble(runId)).thenReturn(new AgentModelContext(
                List.of(AgentModelMessage.user(turn)),
                turn,
                null
        ));
        when(fixture.readIntentResolver().resolve(any())).thenReturn(Optional.of(resolved));
        when(fixture.toolExecutor().execute(any(), any())).thenReturn(new AgentExecutedToolCall(
                new AgentModelToolResult(
                        runId + "-0-0",
                        "search_catalog",
                        "{\"qualificationQuestion\":\"" + serverQuestion + "\"}"
                ),
                true,
                serverQuestion
        ));

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                serverQuestion,
                true
        );
        verify(fixture.messageLedger(), never()).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                untrustedModelQuestion,
                false
        );
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void compoundSearchAndCartRequestWaitsForQualificationBeforeModelOrMutation() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String turn = "find running shoes and add the best pair to my cart";
        String question = "What shoe size do you need?";
        AgentResolvedReadIntent resolved = new AgentResolvedReadIntent(
                new AgentModelToolCall(
                        null,
                        "search_catalog",
                        "{\"query\":\"find running shoes and add the best pair to my cart\"}"
                ),
                turn,
                "add the best pair to my cart"
        );
        AgentModelToolCall prematureMutation = new AgentModelToolCall(
                "call-add",
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-1\"}]}"
        );
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(prematureMutation)))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        when(fixture.contextAssembler().assemble(runId)).thenReturn(new AgentModelContext(
                List.of(AgentModelMessage.user(turn)),
                turn,
                null
        ));
        when(fixture.readIntentResolver().resolve(any())).thenReturn(Optional.of(resolved));
        when(fixture.productClarificationService().unresolvedIntent(any()))
                .thenReturn(Optional.of(clarification()));
        when(fixture.toolExecutor().execute(any(), any())).thenReturn(new AgentExecutedToolCall(
                new AgentModelToolResult(
                        runId + "-0-0",
                        "search_catalog",
                        "{\"qualificationQuestion\":\"" + question + "\"}"
                ),
                true,
                question
        ));

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                question,
                true
        );
        verify(fixture.toolExecutor(), timeout(3000).times(1)).execute(
                any(),
                org.mockito.ArgumentMatchers.argThat(call -> "search_catalog".equals(call.name()))
        );
        verify(fixture.toolExecutor(), never()).execute(
                any(),
                org.mockito.ArgumentMatchers.argThat(call -> "prepare_carts".equals(call.name()))
        );
        verify(fixture.productClarificationService(), never()).unresolvedIntent(any());
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void compoundSearchHandsGroundedResultsToModelAndRejectsProseBeforeTheCartMutation() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String turn = "find running shoes and add the best pair to my cart";
        String prematureQuestion = "What shoe size do you wear?";
        AgentResolvedReadIntent resolved = new AgentResolvedReadIntent(
                new AgentModelToolCall(
                        null,
                        "search_catalog",
                        "{\"query\":\"find running shoes and add the best pair to my cart\"}"
                ),
                turn,
                "add the best pair to my cart"
        );
        AgentModelToolCall mutation = new AgentModelToolCall(
                "call-add",
                "prepare_carts",
                "{\"offers\":[{\"offerKey\":\"offer-1\"}]}"
        );
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model(prematureQuestion, List.of())),
                response(model("", List.of(mutation))),
                response(model("I added the best matching pair to your cart.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        AgentToolDescriptor mutationDescriptor = new AgentToolDescriptor(
                "prepare_carts",
                "Prepare carts",
                "{\"type\":\"object\",\"additionalProperties\":false}",
                "1",
                AgentToolRisk.REVERSIBLE_MUTATION
        );
        when(fixture.contextAssembler().assemble(runId)).thenReturn(new AgentModelContext(
                List.of(AgentModelMessage.user(turn)),
                turn,
                null
        ));
        when(fixture.authorizationPolicy().available(any(), any()))
                .thenReturn(List.of(mutationDescriptor));
        when(fixture.readIntentResolver().resolve(any())).thenReturn(Optional.of(resolved));
        List<String> executionOrder = new CopyOnWriteArrayList<>();
        List<AgentToolExecutionContext> executionContexts = new CopyOnWriteArrayList<>();
        when(fixture.toolExecutor().execute(any(), any())).thenAnswer(invocation -> {
            executionContexts.add(invocation.getArgument(0));
            AgentModelToolCall call = invocation.getArgument(1);
            executionOrder.add(call.name());
            if ("search_catalog".equals(call.name())) {
                return new AgentExecutedToolCall(
                        new AgentModelToolResult(
                                call.id(),
                                call.name(),
                                "{\"products\":[{\"reference\":1,\"canonicalProductKey\":\"shoe-1\","
                                        + "\"recommendedOfferKey\":\"offer-1\"}]}"
                        ),
                        true
                );
            }
            return new AgentExecutedToolCall(
                    new AgentModelToolResult(call.id(), call.name(), "{\"carts\":[{}]}"),
                    true
            );
        });

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "I added the best matching pair to your cart.",
                false
        );
        verify(fixture.messageLedger(), never()).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                prematureQuestion,
                false
        );
        assertThat(executionOrder).containsExactly("search_catalog", "prepare_carts");
        assertThat(executionContexts)
                .extracting(AgentToolExecutionContext::triggeringUserText)
                .containsExactly(turn, "add the best pair to my cart");
        assertThat(model.requests()).hasSize(3);
        assertThat(model.requests().getFirst().messages().getLast().toolResults())
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.toolName()).isEqualTo("search_catalog");
                    assertThat(result.resultJson()).contains("\"recommendedOfferKey\":\"offer-1\"");
                });
        assertThat(model.requests().get(1).messages().getLast().text())
                .contains("no mutation tool has run")
                .contains("prepare_carts");
    }

    @Test
    void explicitOrdinalSimilarityRunsTheVerifiedToolWithoutWaitingForAModelResponse() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID sourceMessageId = UUID.randomUUID();
        String turn = "I like the third one, can you find some similar like those?";
        AgentVisibleProductContext visible = new AgentVisibleProductContext(sourceMessageId, List.of(
                new AgentVisibleProductReference(1, 1, "product-1", "offer-1", "140 Frooty Swim Shorts"),
                new AgentVisibleProductReference(2, 2, "product-2", "offer-2", "1056 - Striped Swim Shorts"),
                new AgentVisibleProductReference(
                        3,
                        3,
                        "swim-shorts-packing-pouch",
                        "offer-3",
                        "Swim Shorts with Packing Pouch"
                ),
                new AgentVisibleProductReference(4, 4, "product-4", "offer-4", "Black Cat Swim Short")
        ));
        AgentModelToolCall similarCall = new AgentModelToolCall(
                null,
                "find_similar_products",
                "{\"canonicalProductKey\":\"swim-shorts-packing-pouch\","
                        + "\"query\":\"products similar to Swim Shorts with Packing Pouch\"}"
        );
        AgentResolvedReadIntent resolved = new AgentResolvedReadIntent(
                similarCall,
                "Swim Shorts with Packing Pouch"
        );
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("This model response must not be needed.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        when(fixture.contextAssembler().assemble(runId)).thenReturn(new AgentModelContext(
                List.of(AgentModelMessage.user(turn)),
                turn,
                visible
        ));
        when(fixture.readIntentResolver().resolve(any())).thenReturn(Optional.of(resolved));
        when(fixture.toolExecutor().execute(any(), any())).thenReturn(new AgentExecutedToolCall(
                new AgentModelToolResult(
                        runId + "-0-0",
                        "find_similar_products",
                        "{\"products\":[{\"reference\":1}],\"hasMore\":false}"
                ),
                true
        ));
        when(fixture.readIntentResolver().completionMessage(eq(resolved), anyString()))
                .thenReturn("I found these products similar to Swim Shorts with Packing Pouch:");

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "I found these products similar to Swim Shorts with Packing Pouch:",
                false
        );
        ArgumentCaptor<AgentToolExecutionContext> contextCaptor =
                ArgumentCaptor.forClass(AgentToolExecutionContext.class);
        ArgumentCaptor<AgentModelToolCall> callCaptor = ArgumentCaptor.forClass(AgentModelToolCall.class);
        verify(fixture.toolExecutor(), timeout(3000)).execute(contextCaptor.capture(), callCaptor.capture());
        assertThat(contextCaptor.getValue().triggeringUserText()).isEqualTo(turn);
        assertThat(contextCaptor.getValue().visibleProductContext()).isEqualTo(visible);
        assertThat(callCaptor.getValue()).isEqualTo(new AgentModelToolCall(
                runId + "-0-0",
                "find_similar_products",
                similarCall.argumentsJson()
        ));
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void emptyResponseRetriesOnceWhenFallbackModelMatchesPrimary() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of())),
                response(model("Recovered on the bounded retry.", List.of()))
        ));
        Fixture fixture = fixture(
                runId,
                conversationId,
                model,
                false,
                properties("primary-model")
        );

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "Recovered on the bounded retry.",
                false
        );
        assertThat(model.requests()).extracting(request -> request.model())
                .containsExactly("primary-model", "primary-model");
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
    void readOnlyToolMayRetryTheSameArgumentsOnceAfterATransientFailure() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String arguments = "{\"query\":\"cool running shoes\"}";
        AgentModelToolCall first = new AgentModelToolCall("call-1", "search_catalog", arguments);
        AgentModelToolCall retry = new AgentModelToolCall("call-2", "search_catalog", arguments);
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(first))),
                response(model("", List.of(retry))),
                response(model("I found these options:", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        when(fixture.toolExecutor().execute(any(), any()))
                .thenReturn(
                        new AgentExecutedToolCall(
                                new AgentModelToolResult(
                                        "call-1",
                                        "search_catalog",
                                        "{\"success\":false,\"retryable\":true}"
                                ),
                                false
                        ),
                        new AgentExecutedToolCall(
                                new AgentModelToolResult(
                                        "call-2",
                                        "search_catalog",
                                        "{\"products\":[{\"reference\":1}]}"
                                ),
                                true
                        )
                );

        coordinator.schedule(runId);

        verify(fixture.toolExecutor(), timeout(3000).times(2)).execute(any(), any());
        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "I found these options:",
                false
        );
        verify(fixture.runService(), never()).failOwnedExecution(
                eq(runId),
                eq(fixture.executionOwner()),
                eq("repeated_tool_call"),
                anyString()
        );
    }

    @Test
    void successfulIdenticalReadsInOneModelBatchExecuteOnceAndReturnEveryToolResult() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String arguments = "{\"canonicalProductKeys\":[\"product-1\",\"product-2\"]}";
        AgentModelToolCall first = new AgentModelToolCall("call-1", "compare_products", arguments);
        AgentModelToolCall duplicate = new AgentModelToolCall("call-2", "compare_products", arguments);
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(first, duplicate))),
                response(model("Here is the comparison:", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        when(fixture.toolExecutor().execute(any(), any()))
                .thenAnswer(invocation -> {
                    AgentModelToolCall call = invocation.getArgument(1);
                    return new AgentExecutedToolCall(
                            new AgentModelToolResult(
                                    call.id(),
                                    call.name(),
                                    "{\"products\":[{\"canonicalProductKey\":\"product-1\"}]}"
                            ),
                            true
                    );
                });

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "Here is the comparison:",
                false
        );
        verify(fixture.toolExecutor()).execute(any(), any());
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().getLast().toolResults())
                .extracting(AgentModelToolResult::toolCallId)
                .containsExactly("call-1", "call-2");
    }

    @Test
    void searchReadsWithDifferentModelArgumentsExecuteSequentiallyAndReturnEveryToolResult() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AgentModelToolCall first = new AgentModelToolCall(
                "call-1",
                "search_catalog",
                "{\"query\":\"running shoes\",\"offset\":0,\"limit\":10}"
        );
        AgentModelToolCall duplicate = new AgentModelToolCall(
                "call-2",
                "search_catalog",
                "{\"query\":\"hiking boots\",\"offset\":50,\"limit\":1}"
        );
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(first, duplicate))),
                response(model("I found these options:", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        when(fixture.toolExecutor().execute(any(), any()))
                .thenAnswer(invocation -> {
                    AgentModelToolCall call = invocation.getArgument(1);
                    return new AgentExecutedToolCall(
                            new AgentModelToolResult(
                                    call.id(),
                                    call.name(),
                                    "{\"products\":[{\"reference\":1}]}"
                            ),
                            true
                    );
                });

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "I found these options:",
                false
        );
        verify(fixture.toolExecutor(), times(2)).execute(any(), any());
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().getLast().toolResults())
                .extracting(AgentModelToolResult::toolCallId)
                .containsExactly("call-1", "call-2");
    }

    @Test
    void failedSearchesWithDistinctArgumentsAllExecuteSequentially() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AgentModelToolCall first = new AgentModelToolCall(
                "call-1",
                "search_catalog",
                "{\"query\":\"running shoes\",\"offset\":0,\"limit\":10}"
        );
        AgentModelToolCall retry = new AgentModelToolCall(
                "call-2",
                "search_catalog",
                "{\"query\":\"hiking boots\",\"offset\":50,\"limit\":1}"
        );
        AgentModelToolCall skipped = new AgentModelToolCall(
                "call-3",
                "search_catalog",
                "{\"query\":\"sandals\",\"offset\":100,\"limit\":100}"
        );
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(first, retry, skipped))),
                response(model("I couldn't complete the search.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<String> executionOrder = new CopyOnWriteArrayList<>();
        AtomicInteger activeReads = new AtomicInteger();
        AtomicInteger maximumActiveReads = new AtomicInteger();
        when(fixture.toolExecutor().execute(any(), any()))
                .thenAnswer(invocation -> {
                    AgentModelToolCall call = invocation.getArgument(1);
                    executionOrder.add(call.id());
                    int active = activeReads.incrementAndGet();
                    maximumActiveReads.accumulateAndGet(active, Math::max);
                    try {
                        if ("call-1".equals(call.id())) {
                            firstStarted.countDown();
                            if (!releaseFirst.await(3, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("The test did not release the first read");
                            }
                        } else {
                            secondStarted.countDown();
                        }
                        return new AgentExecutedToolCall(
                                new AgentModelToolResult(
                                        call.id(),
                                        call.name(),
                                        "{\"success\":false,\"retryable\":true}"
                                ),
                                false
                        );
                    } finally {
                        activeReads.decrementAndGet();
                    }
                });

        coordinator.schedule(runId);

        assertThat(firstStarted.await(3, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(secondStarted.await(250, TimeUnit.MILLISECONDS))
                    .as("an identical read must not start while the prior attempt is still running")
                    .isFalse();
        } finally {
            releaseFirst.countDown();
        }
        assertThat(secondStarted.await(3, TimeUnit.SECONDS)).isTrue();
        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "I couldn't complete the search.",
                false
        );
        verify(fixture.toolExecutor(), timeout(3000).times(3)).execute(any(), any());
        assertThat(executionOrder).containsExactly("call-1", "call-2", "call-3");
        assertThat(maximumActiveReads).hasValue(1);
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().getLast().toolResults())
                .extracting(AgentModelToolResult::toolCallId)
                .containsExactly("call-1", "call-2", "call-3");
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
    void sameUserRunsInDifferentConversationsExecuteConcurrently() throws Exception {
        UUID userId = UUID.randomUUID();
        AgentRun first = queuedRun(
                UUID.randomUUID(),
                UUID.randomUUID(),
                userId,
                Instant.parse("2026-07-23T10:00:00Z")
        );
        AgentRun second = queuedRun(
                UUID.randomUUID(),
                UUID.randomUUID(),
                userId,
                Instant.parse("2026-07-23T10:00:01Z")
        );
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch releaseRuns = new CountDownLatch(1);
        AtomicInteger activeRuns = new AtomicInteger();
        AtomicInteger maximumActiveRuns = new AtomicInteger();
        QueueFixture fixture = queueFixture(List.of(first, second), invocation -> {
            int active = activeRuns.incrementAndGet();
            maximumActiveRuns.accumulateAndGet(active, Math::max);
            bothStarted.countDown();
            try {
                assertThat(releaseRuns.await(3, TimeUnit.SECONDS))
                        .as("both conversation lanes should be able to enter execution")
                        .isTrue();
                return defaultModelContext();
            } finally {
                activeRuns.decrementAndGet();
            }
        });

        coordinator.schedule(first.getId());
        coordinator.schedule(second.getId());

        try {
            assertThat(bothStarted.await(3, TimeUnit.SECONDS))
                    .as("same-user runs in distinct conversations should overlap")
                    .isTrue();
            assertThat(maximumActiveRuns).hasValue(2);
        } finally {
            releaseRuns.countDown();
        }
        assertThat(fixture.completed().await(3, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void runsInOneConversationExecuteInFifoOrderWithoutOverlap() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AgentRun first = queuedRun(
                UUID.randomUUID(),
                conversationId,
                userId,
                Instant.parse("2026-07-23T10:00:00Z")
        );
        AgentRun second = queuedRun(
                UUID.randomUUID(),
                conversationId,
                userId,
                Instant.parse("2026-07-23T10:00:01Z")
        );
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<UUID> executionOrder = new CopyOnWriteArrayList<>();
        AtomicInteger activeRuns = new AtomicInteger();
        AtomicInteger maximumActiveRuns = new AtomicInteger();
        QueueFixture fixture = queueFixture(List.of(first, second), invocation -> {
            UUID runId = invocation.getArgument(0);
            executionOrder.add(runId);
            int active = activeRuns.incrementAndGet();
            maximumActiveRuns.accumulateAndGet(active, Math::max);
            try {
                if (runId.equals(first.getId())) {
                    firstStarted.countDown();
                    assertThat(releaseFirst.await(3, TimeUnit.SECONDS))
                            .as("the test should release the first FIFO entry")
                            .isTrue();
                } else {
                    secondStarted.countDown();
                }
                return defaultModelContext();
            } finally {
                activeRuns.decrementAndGet();
            }
        });

        coordinator.schedule(second.getId());
        coordinator.schedule(first.getId());

        assertThat(firstStarted.await(3, TimeUnit.SECONDS))
                .as("the older run should execute even when the newer run was scheduled first")
                .isTrue();
        try {
            assertThat(secondStarted.await(250, TimeUnit.MILLISECONDS))
                    .as("the next run must not enter the same conversation lane early")
                    .isFalse();
        } finally {
            releaseFirst.countDown();
        }
        assertThat(fixture.completed().await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(executionOrder).containsExactly(first.getId(), second.getId());
        assertThat(maximumActiveRuns).hasValue(1);
    }

    @Test
    void cancelledHeadBetweenQueueLookupAndClaimPromptlyStartsTheNextRun() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AgentRun cancelledHead = queuedRun(
                UUID.randomUUID(),
                conversationId,
                userId,
                Instant.parse("2026-07-23T10:00:00Z")
        );
        AgentRun next = queuedRun(
                UUID.randomUUID(),
                conversationId,
                userId,
                Instant.parse("2026-07-23T10:00:01Z")
        );
        CountDownLatch nextStarted = new CountDownLatch(1);
        List<UUID> executionOrder = new CopyOnWriteArrayList<>();
        QueueFixture fixture = queueFixture(
                List.of(cancelledHead, next),
                invocation -> {
                    UUID runId = invocation.getArgument(0);
                    executionOrder.add(runId);
                    nextStarted.countDown();
                    return defaultModelContext();
                },
                Set.of(cancelledHead.getId())
        );

        coordinator.schedule(cancelledHead.getId());

        assertThat(nextStarted.await(3, TimeUnit.SECONDS))
                .as("a cancelled head must not strand the next queued run")
                .isTrue();
        assertThat(fixture.completed().await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(executionOrder).containsExactly(next.getId());
        verify(fixture.runService()).claim(cancelledHead.getId());
        verify(fixture.runService()).claim(next.getId());
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
    void explicitCartRemovalCannotFinishWithAnUnexecutedSuccessClaim() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AgentModelToolCall remove = new AgentModelToolCall(
                "call-remove",
                "remove_cart_line",
                "{\"cartId\":\"00000000-0000-0000-0000-000000000901\","
                        + "\"cartLineId\":\"00000000-0000-0000-0000-000000000902\"}"
        );
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("I've removed the shorts from your cart.", List.of())),
                response(model("", List.of(remove))),
                response(model("I removed the shorts from your cart.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        AgentToolDescriptor removeDescriptor = new AgentToolDescriptor(
                "remove_cart_line",
                "Remove a cart line",
                "{\"type\":\"object\",\"additionalProperties\":false}",
                "1",
                AgentToolRisk.REVERSIBLE_MUTATION
        );
        when(fixture.contextAssembler().assemble(runId)).thenReturn(new AgentModelContext(
                List.of(AgentModelMessage.user("Remove it from my cart.")),
                "Remove it from my cart."
        ));
        when(fixture.authorizationPolicy().available(any(), any())).thenReturn(List.of(removeDescriptor));
        when(fixture.toolExecutor().execute(any(), any())).thenReturn(new AgentExecutedToolCall(
                new AgentModelToolResult("call-remove", "remove_cart_line", "{\"carts\":[]}"),
                true
        ));

        coordinator.schedule(runId);

        verify(fixture.toolExecutor(), timeout(3000)).execute(
                any(), org.mockito.ArgumentMatchers.argThat(call -> "remove_cart_line".equals(call.name())));
        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "I removed the shorts from your cart.",
                false
        );
        verify(fixture.messageLedger(), never()).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "I've removed the shorts from your cart.",
                false
        );
        assertThat(model.requests()).hasSize(3);
        assertThat(model.requests().get(1).messages().getLast().text())
                .contains("no mutation tool has run")
                .contains("remove_cart_line")
                .contains("Do not claim that the action completed");
    }

    @Test
    void failedCartRemovalCannotFinishWithASuccessClaim() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AgentModelToolCall remove = new AgentModelToolCall(
                "call-remove",
                "remove_cart_line",
                "{\"cartId\":\"00000000-0000-0000-0000-000000000901\","
                        + "\"cartLineId\":\"00000000-0000-0000-0000-000000000902\"}"
        );
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(remove))),
                response(model("I removed the shorts from your cart.", List.of())),
                response(model("The shorts have been removed.", List.of()))
        ));
        Fixture fixture = fixture(runId, conversationId, model, false);
        AgentToolDescriptor removeDescriptor = new AgentToolDescriptor(
                "remove_cart_line",
                "Remove a cart line",
                "{\"type\":\"object\",\"additionalProperties\":false}",
                "1",
                AgentToolRisk.REVERSIBLE_MUTATION
        );
        when(fixture.contextAssembler().assemble(runId)).thenReturn(new AgentModelContext(
                List.of(AgentModelMessage.user("Remove it from my cart.")),
                "Remove it from my cart."
        ));
        when(fixture.authorizationPolicy().available(any(), any())).thenReturn(List.of(removeDescriptor));
        when(fixture.toolExecutor().execute(any(), any())).thenReturn(new AgentExecutedToolCall(
                new AgentModelToolResult(
                        "call-remove",
                        "remove_cart_line",
                        "{\"success\":false,\"message\":\"The cart was unchanged.\"}"
                ),
                false
        ));

        coordinator.schedule(runId);

        verify(fixture.messageLedger(), timeout(3000)).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "I couldn't confirm that requested change. Please check the current state before trying again.",
                false
        );
        verify(fixture.messageLedger(), never()).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "I removed the shorts from your cart.",
                false
        );
        verify(fixture.messageLedger(), never()).appendTerminalAssistant(
                runId,
                fixture.executionOwner(),
                "The shorts have been removed.",
                false
        );
        assertThat(model.requests()).hasSize(3);
        assertThat(model.requests().get(2).messages().getLast().text())
                .contains("has not completed successfully")
                .contains("remove_cart_line")
                .contains("Do not claim that it completed");
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
                defaultModelContext(),
                properties()
        );
    }

    private Fixture fixture(
            UUID runId,
            UUID conversationId,
            ScriptedAgentModelGateway model,
            boolean cancelled,
            AgentModelContext modelContext
    ) {
        return fixture(runId, conversationId, model, cancelled, modelContext, properties());
    }

    private Fixture fixture(
            UUID runId,
            UUID conversationId,
            ScriptedAgentModelGateway model,
            boolean cancelled,
            AgentProperties testProperties
    ) {
        return fixture(runId, conversationId, model, cancelled, defaultModelContext(), testProperties);
    }

    private Fixture fixture(
            UUID runId,
            UUID conversationId,
            ScriptedAgentModelGateway model,
            boolean cancelled,
            AgentModelContext modelContext,
            AgentProperties testProperties
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
        AgentReadIntentResolver readIntentResolver = mock(AgentReadIntentResolver.class);
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
                .userAgent("Meant Browser/1.0")
                .createdAt(Instant.now())
                .build();
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        when(runs.findFirstByConversationIdAndStatusInOrderByCreatedAtAscIdAsc(any(), any()))
                .thenReturn(Optional.of(run), Optional.empty(), Optional.empty());
        when(runService.claim(runId)).thenReturn(Optional.of(executionOwner));
        when(runService.cancellationRequested(runId, executionOwner)).thenReturn(cancelled);
        when(contextAssembler.assemble(runId)).thenReturn(modelContext);
        when(readIntentResolver.resolve(any())).thenReturn(Optional.empty());
        when(registry.descriptors()).thenReturn(List.of(descriptor, mutationDescriptor, comparisonDescriptor));
        when(authorizationPolicy.available(any(), any())).thenReturn(List.of(descriptor, comparisonDescriptor));
        when(jsonSupport.canonicalizeOrOriginal(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tool.descriptor()).thenReturn(descriptor);
        when(registry.required(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            AgentTool selected = mock(AgentTool.class);
            when(selected.descriptor()).thenReturn(switch (name) {
                case "prepare_carts", "remove_cart_line" -> mutationDescriptor;
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
                readIntentResolver,
                messageLedger,
                model,
                mock(AgentMetrics.class),
                jsonSupport,
                testProperties
        );
        return new Fixture(
                contextAssembler,
                authorizationPolicy,
                runService,
                toolExecutor,
                productClarificationService,
                productClarificationContextService,
                readIntentResolver,
                messageLedger,
                executionOwner
        );
    }

    private QueueFixture queueFixture(
            List<AgentRun> queuedRuns,
            Answer<AgentModelContext> contextAnswer
    ) {
        return queueFixture(queuedRuns, contextAnswer, Set.of());
    }

    private QueueFixture queueFixture(
            List<AgentRun> queuedRuns,
            Answer<AgentModelContext> contextAnswer,
            Set<UUID> cancelledBeforeClaim
    ) {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunService runService = mock(AgentRunService.class);
        AgentContextAssembler contextAssembler = mock(AgentContextAssembler.class);
        AgentToolRegistry registry = mock(AgentToolRegistry.class);
        AgentToolAuthorizationPolicy authorizationPolicy = mock(AgentToolAuthorizationPolicy.class);
        AgentToolCallExecutor toolExecutor = mock(AgentToolCallExecutor.class);
        AgentProductClarificationService productClarificationService =
                mock(AgentProductClarificationService.class);
        AgentProductClarificationContextService productClarificationContextService =
                mock(AgentProductClarificationContextService.class);
        AgentReadIntentResolver readIntentResolver = mock(AgentReadIntentResolver.class);
        AgentMessageLedgerService messageLedger = mock(AgentMessageLedgerService.class);
        AgentJsonSupport jsonSupport = mock(AgentJsonSupport.class);
        AgentModelGateway modelGateway = (request, textDeltaConsumer, cancellationRequested) ->
                model("Completed.", List.of());
        AgentToolDescriptor readDescriptor = new AgentToolDescriptor(
                "search_catalog",
                "Search",
                "{\"type\":\"object\",\"additionalProperties\":false}",
                "1",
                AgentToolRisk.READ
        );
        Map<UUID, AgentRun> runsById = new HashMap<>();
        Map<UUID, AgentRunStatus> statesById = new HashMap<>();
        Map<UUID, UUID> ownersById = new HashMap<>();
        Object stateMonitor = new Object();
        CountDownLatch completed = new CountDownLatch(queuedRuns.size() - cancelledBeforeClaim.size());
        queuedRuns.forEach(run -> {
            runsById.put(run.getId(), run);
            statesById.put(run.getId(), AgentRunStatus.QUEUED);
            ownersById.put(run.getId(), UUID.randomUUID());
        });

        when(runs.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(runsById.get(invocation.getArgument(0))));
        when(runs.findFirstByConversationIdAndStatusInOrderByCreatedAtAscIdAsc(any(), any()))
                .thenAnswer(invocation -> {
                    UUID conversationId = invocation.getArgument(0);
                    List<AgentRunStatus> requestedStatuses = invocation.getArgument(1);
                    synchronized (stateMonitor) {
                        return queuedRuns.stream()
                                .filter(run -> run.getConversationId().equals(conversationId))
                                .filter(run -> requestedStatuses.contains(statesById.get(run.getId())))
                                .min(Comparator.comparing(AgentRun::getCreatedAt)
                                        .thenComparing(AgentRun::getId));
                    }
                });
        when(runService.claim(any())).thenAnswer(invocation -> {
            UUID runId = invocation.getArgument(0);
            AgentRun requestedRun = runsById.get(runId);
            synchronized (stateMonitor) {
                if (requestedRun == null || statesById.get(runId) != AgentRunStatus.QUEUED) {
                    return Optional.empty();
                }
                if (cancelledBeforeClaim.contains(runId)) {
                    statesById.put(runId, AgentRunStatus.CANCELLED);
                    return Optional.empty();
                }
                boolean conversationAlreadyRunning = queuedRuns.stream()
                        .filter(run -> run.getConversationId().equals(requestedRun.getConversationId()))
                        .anyMatch(run -> statesById.get(run.getId()) == AgentRunStatus.RUNNING);
                if (conversationAlreadyRunning) {
                    return Optional.empty();
                }
                statesById.put(runId, AgentRunStatus.RUNNING);
                return Optional.of(ownersById.get(runId));
            }
        });
        when(runService.cancellationRequested(any(), any())).thenReturn(false);
        when(contextAssembler.assemble(any())).thenAnswer(contextAnswer);
        when(productClarificationService.unresolvedIntent(any())).thenReturn(Optional.empty());
        when(readIntentResolver.resolve(any())).thenReturn(Optional.empty());
        when(registry.descriptors()).thenReturn(List.of(readDescriptor));
        when(authorizationPolicy.available(any(), any())).thenReturn(List.of(readDescriptor));
        when(messageLedger.appendTerminalAssistant(any(), any(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenAnswer(invocation -> {
                    UUID runId = invocation.getArgument(0);
                    synchronized (stateMonitor) {
                        if (statesById.replace(runId, AgentRunStatus.RUNNING, AgentRunStatus.COMPLETED)) {
                            completed.countDown();
                        }
                    }
                    return null;
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
                readIntentResolver,
                messageLedger,
                modelGateway,
                mock(AgentMetrics.class),
                jsonSupport,
                properties()
        );
        return new QueueFixture(runService, completed);
    }

    private AgentRun queuedRun(
            UUID runId,
            UUID conversationId,
            UUID userId,
            Instant createdAt
    ) {
        return AgentRun.builder()
                .id(runId)
                .conversationId(conversationId)
                .userId(userId)
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.QUEUED)
                .model("primary-model")
                .promptVersion("test-v1")
                .buyerIp("203.0.113.42")
                .createdAt(createdAt)
                .build();
    }

    private AgentModelContext defaultModelContext() {
        return new AgentModelContext(List.of(AgentModelMessage.user("Find shoes")), "Find shoes");
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
        return properties("fallback-model");
    }

    private AgentProperties properties(String fallbackModel) {
        return new AgentProperties(
                true,
                "primary-model",
                fallbackModel,
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
            AgentContextAssembler contextAssembler,
            AgentToolAuthorizationPolicy authorizationPolicy,
            AgentRunService runService,
            AgentToolCallExecutor toolExecutor,
            AgentProductClarificationService productClarificationService,
            AgentProductClarificationContextService productClarificationContextService,
            AgentReadIntentResolver readIntentResolver,
            AgentMessageLedgerService messageLedger,
            UUID executionOwner
    ) {
    }

    private record QueueFixture(AgentRunService runService, CountDownLatch completed) {
    }
}
