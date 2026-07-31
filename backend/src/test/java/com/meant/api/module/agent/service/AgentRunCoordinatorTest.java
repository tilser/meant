package com.meant.api.module.agent.service;

import static com.meant.api.module.agent.support.ScriptedAgentModelGateway.response;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.port.AgentModelGateway;
import com.meant.api.module.agent.service.tool.AgentTool;
import com.meant.api.module.agent.service.tool.AgentToolAuthorizationPolicy;
import com.meant.api.module.agent.service.tool.AgentToolCallExecutor;
import com.meant.api.module.agent.service.tool.AgentToolRegistry;
import com.meant.api.module.agent.support.ScriptedAgentModelGateway;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentRunCoordinatorTest {

    private AgentRunCoordinator coordinator;

    @AfterEach
    void shutDownExecutors() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    void executesModelSelectedToolRoundsAndReturnsResultsToTheModel() {
        AgentModelToolCall search = new AgentModelToolCall(
                "search-1", "search_catalog", "{\"query\":\"shoes\"}");
        AgentModelToolCall pin = new AgentModelToolCall(
                "pin-1", "pin_product", "{\"canonicalProductKey\":\"p1\"}");
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(search))),
                response(model("", List.of(pin))),
                response(model("Hotovo.", List.of()))
        ));
        Fixture fixture = fixture(model, false);
        when(fixture.toolExecutor().execute(any(), any())).thenAnswer(invocation -> {
            AgentModelToolCall call = invocation.getArgument(1);
            return executed(call);
        });

        coordinator.schedule(fixture.run().getId());

        verify(fixture.ledger(), timeout(3_000)).appendTerminalAssistant(
                fixture.run().getId(), fixture.owner(), "Hotovo.", false);
        verify(fixture.toolExecutor(), timeout(3_000).times(2)).execute(any(), any());
        assertThat(model.requests()).hasSize(3);
        assertThat(model.requests().get(1).messages().getLast().toolResults())
                .extracting(AgentModelToolResult::toolCallId)
                .containsExactly("search-1");
        assertThat(model.requests().get(2).messages().getLast().toolResults())
                .extracting(AgentModelToolResult::toolCallId)
                .containsExactly("pin-1");
    }

    @Test
    void runsIndependentReadsInParallelAndMutationsAfterTheReadBatch() throws Exception {
        AgentModelToolCall search = new AgentModelToolCall(
                "search-1", "search_catalog", "{\"query\":\"shoes\"}");
        AgentModelToolCall detail = new AgentModelToolCall(
                "detail-1", "get_product", "{\"canonicalProductKey\":\"p1\"}");
        AgentModelToolCall pin = new AgentModelToolCall(
                "pin-1", "pin_product", "{\"canonicalProductKey\":\"p1\"}");
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("", List.of(search, detail, pin))),
                response(model("Dokončeno.", List.of()))
        ));
        Fixture fixture = fixture(model, false);
        CountDownLatch readsStarted = new CountDownLatch(2);
        AtomicBoolean mutationAfterReads = new AtomicBoolean();
        when(fixture.toolExecutor().execute(any(), any())).thenAnswer(invocation -> {
            AgentModelToolCall call = invocation.getArgument(1);
            if (!"pin_product".equals(call.name())) {
                readsStarted.countDown();
                assertThat(readsStarted.await(2, TimeUnit.SECONDS)).isTrue();
            } else {
                mutationAfterReads.set(readsStarted.getCount() == 0);
            }
            return executed(call);
        });

        coordinator.schedule(fixture.run().getId());

        verify(fixture.ledger(), timeout(3_000)).appendTerminalAssistant(
                fixture.run().getId(), fixture.owner(), "Dokončeno.", false);
        assertThat(mutationAfterReads).isTrue();
        assertThat(model.requests().get(1).messages().getLast().toolResults())
                .extracting(AgentModelToolResult::toolCallId)
                .containsExactly("search-1", "detail-1", "pin-1");
    }

    @Test
    void cancellationBeforeTheFirstTurnStartsNeitherModelNorTool() {
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(List.of(
                response(model("Nemá se spustit.", List.of()))
        ));
        Fixture fixture = fixture(model, true);

        coordinator.schedule(fixture.run().getId());

        verify(fixture.runService(), timeout(3_000)).cancelOwnedExecution(
                fixture.run().getId(), fixture.owner());
        verify(fixture.toolExecutor(), never()).execute(any(), any());
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void oneConversationExecutesQueuedRunsInFifoOrderWithoutOverlap() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AgentRun first = queuedRun(
                UUID.randomUUID(), conversationId, userId, Instant.parse("2026-07-31T10:00:00Z"));
        AgentRun second = queuedRun(
                UUID.randomUUID(), conversationId, userId, Instant.parse("2026-07-31T10:00:01Z"));
        Map<UUID, AgentRunStatus> statuses = new HashMap<>();
        statuses.put(first.getId(), AgentRunStatus.QUEUED);
        statuses.put(second.getId(), AgentRunStatus.QUEUED);
        List<UUID> order = new CopyOnWriteArrayList<>();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        QueueFixture fixture = queueFixture(List.of(first, second), statuses, runId -> {
            order.add(runId);
            maximumActive.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                if (runId.equals(first.getId())) {
                    firstStarted.countDown();
                    assertThat(await(releaseFirst)).isTrue();
                } else {
                    secondStarted.countDown();
                }
                return context();
            } finally {
                active.decrementAndGet();
            }
        }, completed);

        coordinator.schedule(second.getId());
        coordinator.schedule(first.getId());

        assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(secondStarted.await(200, TimeUnit.MILLISECONDS)).isFalse();
        releaseFirst.countDown();
        assertThat(completed.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(order).containsExactly(first.getId(), second.getId());
        assertThat(maximumActive).hasValue(1);
        verify(fixture.ledger(), timeout(3_000).times(2))
                .appendTerminalAssistant(any(), any(), anyString(), any(Boolean.class));
    }

    private Fixture fixture(ScriptedAgentModelGateway model, boolean cancelled) {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunService runService = mock(AgentRunService.class);
        AgentContextAssembler contextAssembler = mock(AgentContextAssembler.class);
        AgentToolRegistry registry = mock(AgentToolRegistry.class);
        AgentToolAuthorizationPolicy authorization = mock(AgentToolAuthorizationPolicy.class);
        AgentToolCallExecutor executor = mock(AgentToolCallExecutor.class);
        AgentMessageLedgerService ledger = mock(AgentMessageLedgerService.class);
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentRun run = queuedRun(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        UUID owner = UUID.randomUUID();
        AtomicReference<AgentRunStatus> status = new AtomicReference<>(AgentRunStatus.QUEUED);
        List<AgentToolDescriptor> descriptors = descriptors();
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(runs.findFirstByConversationIdAndStatusInOrderByCreatedAtAscIdAsc(any(), any()))
                .thenAnswer(invocation -> {
                    List<AgentRunStatus> requested = invocation.getArgument(1);
                    return requested.contains(status.get()) ? Optional.of(run) : Optional.empty();
                });
        when(runService.claim(run.getId())).thenAnswer(invocation ->
                status.compareAndSet(AgentRunStatus.QUEUED, AgentRunStatus.RUNNING)
                        ? Optional.of(owner)
                        : Optional.empty());
        when(runService.cancellationRequested(run.getId(), owner)).thenReturn(cancelled);
        when(runService.cancelOwnedExecution(run.getId(), owner)).thenAnswer(invocation -> {
            status.set(AgentRunStatus.CANCELLED);
            return true;
        });
        when(ledger.appendTerminalAssistant(any(), any(), anyString(), any(Boolean.class)))
                .thenAnswer(invocation -> {
                    status.set(AgentRunStatus.COMPLETED);
                    return null;
                });
        when(contextAssembler.assemble(run.getId())).thenReturn(context());
        when(registry.descriptors()).thenReturn(descriptors);
        when(authorization.available(any(), any())).thenReturn(descriptors);
        registerDescriptors(registry, descriptors);
        when(json.canonicalizeOrOriginal(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        coordinator = coordinator(
                runs, runService, contextAssembler, registry, authorization, executor, ledger, model, json);
        return new Fixture(run, owner, runService, executor, ledger);
    }

    private QueueFixture queueFixture(
            List<AgentRun> queuedRuns,
            Map<UUID, AgentRunStatus> statuses,
            java.util.function.Function<UUID, AgentModelContext> contextFunction,
            CountDownLatch completed
    ) {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunService runService = mock(AgentRunService.class);
        AgentContextAssembler contextAssembler = mock(AgentContextAssembler.class);
        AgentToolRegistry registry = mock(AgentToolRegistry.class);
        AgentToolAuthorizationPolicy authorization = mock(AgentToolAuthorizationPolicy.class);
        AgentToolCallExecutor executor = mock(AgentToolCallExecutor.class);
        AgentMessageLedgerService ledger = mock(AgentMessageLedgerService.class);
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        Map<UUID, AgentRun> runsById = new HashMap<>();
        Map<UUID, UUID> owners = new HashMap<>();
        queuedRuns.forEach(run -> {
            runsById.put(run.getId(), run);
            owners.put(run.getId(), UUID.randomUUID());
        });
        Object monitor = new Object();
        when(runs.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(runsById.get(invocation.getArgument(0))));
        when(runs.findFirstByConversationIdAndStatusInOrderByCreatedAtAscIdAsc(any(), any()))
                .thenAnswer(invocation -> {
                    UUID conversationId = invocation.getArgument(0);
                    List<AgentRunStatus> requested = invocation.getArgument(1);
                    synchronized (monitor) {
                        return queuedRuns.stream()
                                .filter(run -> run.getConversationId().equals(conversationId))
                                .filter(run -> requested.contains(statuses.get(run.getId())))
                                .min(Comparator.comparing(AgentRun::getCreatedAt)
                                        .thenComparing(AgentRun::getId));
                    }
                });
        when(runService.claim(any())).thenAnswer(invocation -> {
            UUID runId = invocation.getArgument(0);
            synchronized (monitor) {
                if (statuses.get(runId) != AgentRunStatus.QUEUED) {
                    return Optional.empty();
                }
                statuses.put(runId, AgentRunStatus.RUNNING);
                return Optional.of(owners.get(runId));
            }
        });
        when(runService.cancellationRequested(any(), any())).thenReturn(false);
        when(contextAssembler.assemble(any())).thenAnswer(invocation ->
                contextFunction.apply(invocation.getArgument(0)));
        when(registry.descriptors()).thenReturn(List.of());
        when(authorization.available(any(), any())).thenReturn(List.of());
        when(ledger.appendTerminalAssistant(any(), any(), anyString(), any(Boolean.class)))
                .thenAnswer(invocation -> {
                    UUID runId = invocation.getArgument(0);
                    synchronized (monitor) {
                        statuses.put(runId, AgentRunStatus.COMPLETED);
                    }
                    completed.countDown();
                    return null;
                });
        AgentModelGateway model = (request, delta, cancellation) -> model("Hotovo.", List.of());
        coordinator = coordinator(
                runs, runService, contextAssembler, registry, authorization, executor, ledger, model, json);
        return new QueueFixture(ledger);
    }

    private AgentRunCoordinator coordinator(
            AgentRunRepository runs,
            AgentRunService runService,
            AgentContextAssembler contextAssembler,
            AgentToolRegistry registry,
            AgentToolAuthorizationPolicy authorization,
            AgentToolCallExecutor executor,
            AgentMessageLedgerService ledger,
            AgentModelGateway model,
            AgentJsonSupport json
    ) {
        return new AgentRunCoordinator(
                runs,
                runService,
                contextAssembler,
                registry,
                authorization,
                executor,
                ledger,
                model,
                (history, assistantText, calls, results, definitions) -> {
                    List<AgentModelMessage> updated = new ArrayList<>(history);
                    updated.add(AgentModelMessage.assistant(assistantText, calls));
                    updated.add(AgentModelMessage.tools(results));
                    return List.copyOf(updated);
                },
                mock(AgentMetrics.class),
                json,
                properties()
        );
    }

    private void registerDescriptors(AgentToolRegistry registry, List<AgentToolDescriptor> descriptors) {
        when(registry.required(anyString())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            AgentToolDescriptor descriptor = descriptors.stream()
                    .filter(candidate -> candidate.name().equals(name))
                    .findFirst()
                    .orElseThrow();
            AgentTool tool = mock(AgentTool.class);
            when(tool.descriptor()).thenReturn(descriptor);
            return tool;
        });
    }

    private List<AgentToolDescriptor> descriptors() {
        return List.of(
                descriptor("search_catalog", AgentToolRisk.READ),
                descriptor("get_product", AgentToolRisk.READ),
                descriptor("pin_product", AgentToolRisk.REVERSIBLE_MUTATION)
        );
    }

    private AgentToolDescriptor descriptor(String name, AgentToolRisk risk) {
        return new AgentToolDescriptor(
                name,
                name,
                "{\"type\":\"object\",\"additionalProperties\":true}",
                "1",
                risk
        );
    }

    private AgentExecutedToolCall executed(AgentModelToolCall call) {
        return new AgentExecutedToolCall(
                new AgentModelToolResult(call.id(), call.name(), "{\"ok\":true}"),
                true
        );
    }

    private AgentModelContext context() {
        return new AgentModelContext(List.of(AgentModelMessage.user("Pokračuj.")), "Pokračuj.");
    }

    private boolean await(CountDownLatch latch) {
        try {
            return latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
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

    private AgentRun queuedRun(UUID runId, UUID conversationId, UUID userId, Instant createdAt) {
        return AgentRun.builder()
                .id(runId)
                .conversationId(conversationId)
                .userId(userId)
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.QUEUED)
                .model("primary-model")
                .promptVersion("test-v1")
                .buyerIp("203.0.113.42")
                .userAgent("Meant Test")
                .language("cs")
                .createdAt(createdAt)
                .build();
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
                64_000,
                24_000,
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
            AgentRun run,
            UUID owner,
            AgentRunService runService,
            AgentToolCallExecutor toolExecutor,
            AgentMessageLedgerService ledger
    ) {
    }

    private record QueueFixture(AgentMessageLedgerService ledger) {
    }
}
