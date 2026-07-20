package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentRunEventType;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.service.dto.AgentEventPayload;
import com.meant.api.module.agent.service.dto.AgentExecutedToolCall;
import com.meant.api.module.agent.service.dto.AgentModelContext;
import com.meant.api.module.agent.service.dto.AgentModelMessage;
import com.meant.api.module.agent.service.dto.AgentModelRequest;
import com.meant.api.module.agent.service.dto.AgentModelResponse;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentModelToolResult;
import com.meant.api.module.agent.service.dto.AgentProductClarification;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import com.meant.api.module.agent.service.port.AgentModelGateway;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunCoordinator {

    private static final String WAITING_PREFIX = "WAITING_FOR_USER:";
    private static final String RECOVERY_MESSAGE =
            "I couldn't finish the remaining steps safely. Review the completed actions above; no further actions were started.";
    private static final String COMPARE_PRODUCTS_TOOL = "compare_products";
    private static final Pattern DIRECT_COMPARISON_REQUEST = Pattern.compile(
            "(?iu)^\\s*(?:please\\s+)?(?:(?:can|could|would|will)\\s+you\\s+)?compare\\b"
    );
    private static final int QUEUED_RUN_PAGE_SIZE = 100;

    private final AgentRunRepository runRepository;
    private final AgentRunService runService;
    private final AgentContextAssembler contextAssembler;
    private final AgentToolRegistry toolRegistry;
    private final AgentToolAuthorizationPolicy authorizationPolicy;
    private final AgentToolCallExecutor toolCallExecutor;
    private final AgentProductClarificationService productClarificationService;
    private final AgentProductClarificationContextService productClarificationContextService;
    private final AgentMessageLedgerService messageLedgerService;
    private final AgentModelGateway modelGateway;
    private final AgentMetrics metrics;
    private final AgentJsonSupport jsonSupport;
    private final AgentProperties properties;
    private final ExecutorService runExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ExecutorService parallelReadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Map<UUID, ReentrantLock> conversationLocks = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> scheduledRuns = ConcurrentHashMap.newKeySet();

    public void schedule(UUID runId) {
        if (!properties.enabled() || !scheduledRuns.add(runId)) {
            return;
        }
        runExecutor.submit(() -> executeScheduled(runId));
    }

    public void scheduleQueuedRuns() {
        if (!properties.enabled()) {
            return;
        }
        UUID afterId = null;
        List<AgentRun> page;
        do {
            page = afterId == null
                    ? runRepository.findByStatusOrderByIdAsc(
                            AgentRunStatus.QUEUED,
                            PageRequest.of(0, QUEUED_RUN_PAGE_SIZE)
                    )
                    : runRepository.findByStatusAndIdGreaterThanOrderByIdAsc(
                            AgentRunStatus.QUEUED,
                            afterId,
                            PageRequest.of(0, QUEUED_RUN_PAGE_SIZE)
                    );
            page.stream().map(AgentRun::getId).forEach(this::schedule);
            if (!page.isEmpty()) {
                afterId = page.getLast().getId();
            }
        } while (page.size() == QUEUED_RUN_PAGE_SIZE);
    }

    private void executeScheduled(UUID runId) {
        UUID conversationId = runRepository.findById(runId)
                .map(AgentRun::getConversationId)
                .orElse(null);
        if (conversationId == null) {
            scheduledRuns.remove(runId);
            return;
        }
        ReentrantLock lock = conversationLocks.computeIfAbsent(conversationId, ignored -> new ReentrantLock());
        lock.lock();
        UUID executionOwner = null;
        ScheduledFuture<?> heartbeat = null;
        try {
            executionOwner = runService.claim(runId).orElse(null);
            if (executionOwner == null) {
                return;
            }
            heartbeat = startHeartbeat(runId, executionOwner);
            executeLoop(runId, conversationId, executionOwner);
        } catch (CancellationException exception) {
            if (executionOwner != null) {
                runService.cancelOwnedExecution(runId, executionOwner);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "Agent run failed. runId={}, conversationId={}, failureType={}",
                    runId,
                    conversationId,
                    exception.getClass().getName(),
                    exception
            );
            if (executionOwner != null) {
                safelyPersistFailure(runId, executionOwner, "agent_run_failed", RECOVERY_MESSAGE);
            }
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
            lock.unlock();
            scheduledRuns.remove(runId);
            if (executionOwner != null) {
                scheduleNextQueuedRun(conversationId);
            }
        }
    }

    private ScheduledFuture<?> startHeartbeat(UUID runId, UUID executionOwner) {
        long leaseMillis = Math.max(1L, properties.staleRunAge().toMillis());
        long intervalMillis = Math.max(25L, Math.min(30_000L, leaseMillis / 3L));
        return heartbeatExecutor.scheduleWithFixedDelay(
                () -> renewLeaseSafely(runId, executionOwner),
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    private void renewLeaseSafely(UUID runId, UUID executionOwner) {
        try {
            runService.renewLease(runId, executionOwner);
        } catch (RuntimeException exception) {
            log.warn(
                    "Could not renew agent run lease. runId={}, failureType={}",
                    runId,
                    exception.getClass().getName()
            );
        }
    }

    private void scheduleNextQueuedRun(UUID conversationId) {
        if (runRepository.findFirstByConversationIdAndStatusInOrderByCreatedAtAsc(
                conversationId,
                List.of(AgentRunStatus.RUNNING)
        ).isPresent()) {
            return;
        }
        runRepository.findFirstByConversationIdAndStatusInOrderByCreatedAtAsc(
                        conversationId,
                        List.of(AgentRunStatus.QUEUED)
                )
                .map(AgentRun::getId)
                .ifPresent(this::schedule);
    }

    private void executeLoop(UUID runId, UUID conversationId, UUID executionOwner) {
        long deadline = System.nanoTime() + properties.runDeadline().toNanos();
        AgentModelContext context = contextAssembler.assemble(runId);
        AgentRun run = runRepository.findById(runId).orElseThrow(AgentException::notFound);
        List<AgentModelMessage> modelMessages = new ArrayList<>(context.messages());
        AgentToolExecutionContext toolContext = new AgentToolExecutionContext(
                run.getUserId(),
                conversationId,
                runId,
                run.getTriggeringMessageId(),
                context.triggeringUserText()
        ).withVisibleProductContext(context.visibleProductContext())
                .withPendingProductClarification(context.pendingProductClarification())
                .withExecutionOwner(executionOwner)
                .withBuyerIp(run.getBuyerIp());
        Map<String, Integer> perToolCounts = new HashMap<>();
        Map<String, Integer> repeatedCalls = new HashMap<>();
        int totalToolCalls = 0;
        boolean mutationAttempted = false;
        boolean comparisonToolRequired = requiresComparisonArtifact(context);

        for (int iteration = 0; iteration < properties.maximumModelIterations(); iteration++) {
            requireActive(runId, executionOwner, deadline);
            if (!mutationAttempted) {
                Optional<AgentProductClarification> unresolvedIntent =
                        productClarificationService.unresolvedIntent(toolContext);
                if (unresolvedIntent.isPresent()) {
                    waitForProductClarification(runId, executionOwner, unresolvedIntent.get());
                    return;
                }
            }
            List<AgentToolDescriptor> descriptors = authorizationPolicy.available(
                    toolContext,
                    toolRegistry.descriptors()
            );
            String requiredToolName = comparisonToolRequired && descriptors.stream()
                    .anyMatch(descriptor -> COMPARE_PRODUCTS_TOOL.equals(descriptor.name()))
                    ? COMPARE_PRODUCTS_TOOL
                    : null;
            DeltaWriter deltaWriter = new DeltaWriter(runId, executionOwner);
            AgentModelResponse response = modelTurnWithFallback(
                    modelMessages,
                    descriptors,
                    requiredToolName,
                    deltaWriter
            );
            requireActive(runId, executionOwner, deadline);
            deltaWriter.flush();
            runService.recordIteration(runId, executionOwner, response.usage());

            List<AgentModelToolCall> calls = normalizeCalls(runId, iteration, response.toolCalls());
            comparisonToolRequired = comparisonToolRequired
                    && calls.stream().noneMatch(call -> COMPARE_PRODUCTS_TOOL.equals(call.name()));
            if (calls.isEmpty()) {
                finish(runId, executionOwner, response.text());
                return;
            }
            modelMessages.add(AgentModelMessage.assistant(response.text(), calls));
            totalToolCalls += calls.size();
            if (totalToolCalls > properties.maximumTotalToolInvocations()) {
                terminateForLimit(runId, executionOwner, "tool_limit", "I reached the safe tool limit before finishing.");
                return;
            }
            for (AgentModelToolCall call : calls) {
                int toolCount = perToolCounts.merge(call.name(), 1, Integer::sum);
                if (toolCount > properties.maximumPerToolInvocations()) {
                    terminateForLimit(
                            runId,
                            executionOwner,
                            "per_tool_limit",
                            "I stopped because one action repeated too many times."
                    );
                    return;
                }
                String fingerprint = fingerprint(call);
                int repeatCount = repeatedCalls.merge(fingerprint, 1, Integer::sum);
                if (repeatCount >= properties.repeatedIdenticalToolCallThreshold()) {
                    terminateForLimit(
                            runId,
                            executionOwner,
                            "repeated_tool_call",
                            "I stopped a repeated action loop before changing anything else."
                    );
                    return;
                }
            }

            Optional<AgentProductClarification> clarification =
                    productClarificationService.preflight(toolContext, calls);
            if (clarification.isPresent()) {
                requireActive(runId, executionOwner, deadline);
                waitForProductClarification(runId, executionOwner, clarification.get());
                return;
            }

            List<AgentModelToolResult> results = executeTools(toolContext, calls, executionOwner, deadline);
            mutationAttempted = mutationAttempted
                    || calls.stream().anyMatch(call -> risk(call) != AgentToolRisk.READ);
            modelMessages.add(AgentModelMessage.tools(results));
        }
        terminateForLimit(
                runId,
                executionOwner,
                "iteration_limit",
                "I reached the safe reasoning limit before finishing."
        );
    }

    private AgentModelResponse modelTurnWithFallback(
            List<AgentModelMessage> messages,
            List<AgentToolDescriptor> descriptors,
            String requiredToolName,
            DeltaWriter deltaWriter
    ) {
        AtomicBoolean emitted = new AtomicBoolean();
        long primaryStarted = System.nanoTime();
        try {
            AgentModelResponse response = modelGateway.turn(
                    request(properties.model(), messages, descriptors, requiredToolName),
                    delta -> {
                        if (delta != null && !delta.isBlank()) {
                            emitted.set(true);
                        }
                        deltaWriter.accept(delta);
                    },
                    deltaWriter::cancelled
            );
            requireUsableModelResponse(response);
            metrics.modelTurn(properties.model(), "success", System.nanoTime() - primaryStarted);
            return response;
        } catch (CancellationException cancellation) {
            metrics.modelTurn(properties.model(), "cancelled", System.nanoTime() - primaryStarted);
            throw cancellation;
        } catch (RuntimeException primaryFailure) {
            metrics.modelTurn(properties.model(), "failure", System.nanoTime() - primaryStarted);
            if (emitted.get() || properties.fallbackModel().equals(properties.model())) {
                throw primaryFailure;
            }
            long fallbackStarted = System.nanoTime();
            try {
                AgentModelResponse response = modelGateway.turn(
                        request(properties.fallbackModel(), messages, descriptors, requiredToolName),
                        deltaWriter::accept,
                        deltaWriter::cancelled
                );
                requireUsableModelResponse(response);
                metrics.modelTurn(
                        properties.fallbackModel(),
                        "success",
                        System.nanoTime() - fallbackStarted
                );
                return response;
            } catch (CancellationException cancellation) {
                metrics.modelTurn(
                        properties.fallbackModel(),
                        "cancelled",
                        System.nanoTime() - fallbackStarted
                );
                throw cancellation;
            } catch (RuntimeException fallbackFailure) {
                metrics.modelTurn(
                        properties.fallbackModel(),
                        "failure",
                        System.nanoTime() - fallbackStarted
                );
                throw fallbackFailure;
            }
        }
    }

    private void requireUsableModelResponse(AgentModelResponse response) {
        if (response == null || (response.text().isBlank() && response.toolCalls().isEmpty())) {
            throw new IllegalStateException("Agent model returned neither text nor tool calls");
        }
    }

    private AgentModelRequest request(
            String model,
            List<AgentModelMessage> messages,
            List<AgentToolDescriptor> descriptors,
            String requiredToolName
    ) {
        return new AgentModelRequest(
                model,
                List.copyOf(messages),
                descriptors.stream().map(AgentToolDescriptor::modelDefinition).toList(),
                properties.temperature(),
                properties.maximumOutputTokens(),
                requiredToolName
        );
    }

    private boolean requiresComparisonArtifact(AgentModelContext context) {
        if (context.triggeringUserText() == null
                || !DIRECT_COMPARISON_REQUEST.matcher(context.triggeringUserText()).find()
                || context.visibleProductContext() == null) {
            return false;
        }
        return context.visibleProductContext().products().stream()
                .map(AgentVisibleProductReference::canonicalProductKey)
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .limit(2)
                .count() == 2;
    }

    private List<AgentModelToolResult> executeTools(
            AgentToolExecutionContext context,
            List<AgentModelToolCall> calls,
            UUID executionOwner,
            long deadline
    ) {
        Map<String, AgentModelToolResult> results = new LinkedHashMap<>();
        List<AgentModelToolCall> reads = calls.stream()
                .filter(call -> risk(call) == AgentToolRisk.READ)
                .toList();
        List<AgentModelToolCall> mutations = calls.stream()
                .filter(call -> risk(call) != AgentToolRisk.READ)
                .toList();

        int maximumParallel = properties.maximumParallelReadTools();
        for (int offset = 0; offset < reads.size(); offset += maximumParallel) {
            requireActive(context.runId(), executionOwner, deadline);
            List<AgentModelToolCall> batch = reads.subList(offset, Math.min(reads.size(), offset + maximumParallel));
            List<CompletableFuture<AgentExecutedToolCall>> futures = batch.stream()
                    .map(call -> CompletableFuture.supplyAsync(
                            () -> toolCallExecutor.execute(context, call),
                            parallelReadExecutor
                    ))
                    .toList();
            for (int index = 0; index < futures.size(); index++) {
                long remainingNanos = Math.max(1, deadline - System.nanoTime());
                try {
                    AgentExecutedToolCall executed = futures.get(index).get(remainingNanos, TimeUnit.NANOSECONDS);
                    results.put(batch.get(index).id(), executed.modelResult());
                } catch (java.util.concurrent.TimeoutException exception) {
                    futures.forEach(future -> future.cancel(true));
                    throw new AgentException(
                            org.springframework.http.HttpStatus.REQUEST_TIMEOUT,
                            com.meant.api.common.constant.ApiErrorCode.BAD_REQUEST,
                            "The agent run exceeded its time limit."
                    );
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("Agent run was interrupted");
                } catch (java.util.concurrent.ExecutionException exception) {
                    if (exception.getCause() instanceof CancellationException cancellation) {
                        throw cancellation;
                    }
                    throw new IllegalStateException("Agent read tool failed unexpectedly", exception.getCause());
                }
            }
        }
        for (AgentModelToolCall mutation : mutations) {
            requireActive(context.runId(), executionOwner, deadline);
            AgentExecutedToolCall executed = toolCallExecutor.execute(context, mutation);
            results.put(mutation.id(), executed.modelResult());
        }
        return calls.stream().map(call -> results.get(call.id())).toList();
    }

    private AgentToolRisk risk(AgentModelToolCall call) {
        try {
            return toolRegistry.required(call.name()).descriptor().riskClass();
        } catch (AgentException exception) {
            return AgentToolRisk.READ;
        }
    }

    private void finish(UUID runId, UUID executionOwner, String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        boolean waiting = text.startsWith(WAITING_PREFIX);
        if (waiting) {
            text = text.substring(WAITING_PREFIX.length()).trim();
        }
        if (text.isBlank()) {
            text = RECOVERY_MESSAGE;
        }
        messageLedgerService.appendTerminalAssistant(runId, executionOwner, text, waiting);
    }

    private void waitForProductClarification(
            UUID runId,
            UUID executionOwner,
            AgentProductClarification clarification
    ) {
        String question = productClarificationService.question(clarification);
        String contentJson = productClarificationContextService.serialize(clarification);
        messageLedgerService.appendTerminalAssistant(
                runId,
                executionOwner,
                question,
                contentJson,
                true
        );
    }

    private void terminateForLimit(UUID runId, UUID executionOwner, String code, String message) {
        messageLedgerService.appendAssistant(runId, executionOwner, message);
        runService.failOwnedExecution(runId, executionOwner, code, message);
    }

    private void requireActive(UUID runId, UUID executionOwner, long deadline) {
        if (runService.cancellationRequested(runId, executionOwner)) {
            throw new CancellationException("Agent run cancellation was requested");
        }
        if (System.nanoTime() >= deadline) {
            throw new AgentException(
                    org.springframework.http.HttpStatus.REQUEST_TIMEOUT,
                    com.meant.api.common.constant.ApiErrorCode.BAD_REQUEST,
                    "The agent run exceeded its time limit."
            );
        }
    }

    private List<AgentModelToolCall> normalizeCalls(UUID runId, int iteration, List<AgentModelToolCall> calls) {
        List<AgentModelToolCall> normalized = new ArrayList<>();
        for (int index = 0; index < calls.size(); index++) {
            AgentModelToolCall call = calls.get(index);
            String id = call.id();
            if (id == null || id.isBlank()) {
                id = runId + "-" + iteration + "-" + index;
            }
            String name = call.name() == null ? "" : call.name().trim();
            String arguments = call.argumentsJson() == null || call.argumentsJson().isBlank()
                    ? "{}"
                    : jsonSupport.canonicalizeOrOriginal(call.argumentsJson());
            normalized.add(new AgentModelToolCall(id, name, arguments));
        }
        return List.copyOf(normalized);
    }

    private String fingerprint(AgentModelToolCall call) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((call.name() + ":" + call.argumentsJson()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void safelyPersistFailure(UUID runId, UUID executionOwner, String code, String message) {
        try {
            messageLedgerService.appendAssistant(runId, executionOwner, message);
        } catch (RuntimeException ignored) {
            log.debug("Could not append an agent recovery message. runId={}", runId);
        }
        try {
            runService.failOwnedExecution(runId, executionOwner, code, message);
        } catch (RuntimeException ignored) {
            log.debug("Could not persist an agent run failure. runId={}", runId);
        }
    }

    @PreDestroy
    void shutdown() {
        runExecutor.shutdownNow();
        parallelReadExecutor.shutdownNow();
        heartbeatExecutor.shutdownNow();
    }

    private final class DeltaWriter {

        private static final int FLUSH_CHARACTERS = 96;
        private final UUID runId;
        private final UUID executionOwner;
        private final StringBuilder buffer = new StringBuilder();

        private DeltaWriter(UUID runId, UUID executionOwner) {
            this.runId = runId;
            this.executionOwner = executionOwner;
        }

        private void accept(String delta) {
            if (delta == null || delta.isEmpty()) {
                return;
            }
            buffer.append(delta);
            if (buffer.length() >= FLUSH_CHARACTERS) {
                flush();
            }
        }

        private void flush() {
            if (buffer.isEmpty()) {
                return;
            }
            String delta = buffer.toString();
            buffer.setLength(0);
            runService.append(
                    runId,
                    executionOwner,
                    AgentRunEventType.ASSISTANT_DELTA,
                    AgentEventPayload.text(delta)
            );
        }

        private boolean cancelled() {
            return runService.cancellationRequested(runId, executionOwner);
        }
    }
}
