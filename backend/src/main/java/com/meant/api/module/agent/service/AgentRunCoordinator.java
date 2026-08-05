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
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.port.AgentModelGateway;
import com.meant.api.module.agent.service.port.AgentToolConversationHistory;
import com.meant.api.module.agent.service.tool.AgentToolAuthorizationPolicy;
import com.meant.api.module.agent.service.tool.AgentToolCallExecutor;
import com.meant.api.module.agent.service.tool.AgentToolRegistry;
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
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunCoordinator {

    private static final String SEARCH_CATALOG_TOOL = "search_catalog";
    private static final int MAXIMUM_READ_ATTEMPTS_PER_BATCH = 2;
    private static final int QUEUED_RUN_PAGE_SIZE = 100;

    private final AgentRunRepository runRepository;
    private final AgentRunService runService;
    private final AgentContextAssembler contextAssembler;
    private final AgentToolRegistry toolRegistry;
    private final AgentToolAuthorizationPolicy authorizationPolicy;
    private final AgentToolCallExecutor toolCallExecutor;
    private final AgentMessageLedgerService messageLedgerService;
    private final AgentModelGateway modelGateway;
    private final AgentToolConversationHistory toolConversationHistory;
    private final AgentMetrics metrics;
    private final AgentJsonSupport jsonSupport;
    private final AgentProperties properties;
    private final ExecutorService runExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ExecutorService parallelReadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Map<UUID, ConversationLane> conversationLanes = new ConcurrentHashMap<>();
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
        AgentRun scheduledRun = runRepository.findById(runId).orElse(null);
        if (scheduledRun == null) {
            scheduledRuns.remove(runId);
            return;
        }
        UUID conversationId = scheduledRun.getConversationId();
        ConversationLane lane = retainConversationLane(conversationId);
        lane.lock.lock();
        UUID executionOwner = null;
        ScheduledFuture<?> heartbeat = null;
        try {
            UUID nextQueuedRunId = runRepository.findFirstByConversationIdAndStatusInOrderByCreatedAtAscIdAsc(
                            conversationId,
                            List.of(AgentRunStatus.QUEUED)
                    )
                    .map(AgentRun::getId)
                    .orElse(null);
            if (!runId.equals(nextQueuedRunId)) {
                if (nextQueuedRunId != null) {
                    schedule(nextQueuedRunId);
                }
                return;
            }
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
                safelyPersistFailure(runId, executionOwner, "agent_run_failed", null);
            }
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
            lane.lock.unlock();
            releaseConversationLane(conversationId, lane);
            scheduledRuns.remove(runId);
            scheduleNextQueuedRun(conversationId);
        }
    }

    private ScheduledFuture<?> startHeartbeat(UUID runId, UUID executionOwner) {
        long leaseMillis = Math.max(1L, properties.staleRunAge().toMillis());
        long intervalMillis = Math.clamp(leaseMillis / 3L, 25L, 30_000L);
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
        if (runRepository.findFirstByConversationIdAndStatusInOrderByCreatedAtAscIdAsc(
                conversationId,
                List.of(AgentRunStatus.RUNNING)
        ).isPresent()) {
            return;
        }
        runRepository.findFirstByConversationIdAndStatusInOrderByCreatedAtAscIdAsc(
                        conversationId,
                        List.of(AgentRunStatus.QUEUED)
                )
                .map(AgentRun::getId)
                .ifPresent(this::schedule);
    }

    private ConversationLane retainConversationLane(UUID conversationId) {
        return conversationLanes.compute(conversationId, (ignored, current) -> {
            ConversationLane retained = current == null ? new ConversationLane() : current;
            retained.references += 1;
            return retained;
        });
    }

    private void releaseConversationLane(UUID conversationId, ConversationLane lane) {
        conversationLanes.computeIfPresent(conversationId, (ignored, current) -> {
            if (current != lane) {
                return current;
            }
            current.references -= 1;
            return current.references == 0 ? null : current;
        });
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
        ).withAnonymousUser(run.isAnonymousUser())
                .withVisibleProductContext(context.visibleProductContext())
                .withMerchantId(context.merchantId())
                .withExecutionOwner(executionOwner)
                .withBuyerIp(run.getBuyerIp())
                .withUserAgent(run.getUserAgent())
                .withLanguage(run.getLanguage());
        Map<String, Integer> perToolCounts = new HashMap<>();
        Map<String, Integer> repeatedCalls = new HashMap<>();
        int totalToolCalls = 0;

        for (int iteration = 0; iteration < properties.maximumModelIterations(); iteration++) {
            requireActive(runId, executionOwner, deadline);
            List<AgentToolDescriptor> descriptors = authorizationPolicy.available(
                    toolContext,
                    toolRegistry.descriptors()
            );
            DeltaWriter deltaWriter = new DeltaWriter(runId, executionOwner);
            AgentModelResponse response = modelTurnWithFallback(
                    modelMessages,
                    descriptors,
                    deltaWriter
            );
            requireActive(runId, executionOwner, deadline);
            deltaWriter.flush();
            runService.recordIteration(runId, executionOwner, response.usage());

            List<AgentModelToolCall> calls = normalizeCalls(runId, iteration, response.toolCalls());
            if (calls.isEmpty()) {
                finish(runId, executionOwner, response.text());
                return;
            }
            totalToolCalls += calls.size();
            if (totalToolCalls > properties.maximumTotalToolInvocations()) {
                log.warn(
                        "Agent total tool-call guard triggered. runId={}, iteration={}, totalToolCalls={}, limit={}",
                        runId,
                        iteration,
                        totalToolCalls,
                        properties.maximumTotalToolInvocations()
                );
                terminateForLimit(runId, executionOwner, "tool_limit");
                return;
            }
            for (AgentModelToolCall call : calls) {
                int toolCount = perToolCounts.merge(call.name(), 1, Integer::sum);
                if (toolCount > properties.maximumPerToolInvocations()) {
                    log.warn(
                            "Agent per-tool call guard triggered. runId={}, iteration={}, toolName={}, "
                                    + "toolInvocationCount={}, limit={}",
                            runId,
                            iteration,
                            call.name(),
                            toolCount,
                            properties.maximumPerToolInvocations()
                    );
                    terminateForLimit(
                            runId,
                            executionOwner,
                            "per_tool_limit"
                    );
                    return;
                }
                String fingerprint = fingerprint(call);
                int repeatCount = repeatedCalls.merge(fingerprint, 1, Integer::sum);
                boolean repeatedMutation = risk(call) != AgentToolRisk.READ
                        && repeatCount >= properties.repeatedIdenticalToolCallThreshold();
                boolean exhaustedReadRetry = risk(call) == AgentToolRisk.READ
                        && repeatCount > properties.repeatedIdenticalToolCallThreshold();
                if (repeatedMutation || exhaustedReadRetry) {
                    log.warn(
                            "Agent repeated tool-call guard triggered. runId={}, iteration={}, toolName={}, "
                                    + "repeatCount={}, threshold={}, argumentsFingerprint={}",
                            runId,
                            iteration,
                            call.name(),
                            repeatCount,
                            properties.repeatedIdenticalToolCallThreshold(),
                            fingerprint
                    );
                    terminateForLimit(
                            runId,
                            executionOwner,
                            "repeated_tool_call"
                    );
                    return;
                }
            }

            ToolBatchResult batchResult = executeTools(toolContext, calls, executionOwner, deadline);
            modelMessages = new ArrayList<>(toolConversationHistory.afterToolExecution(
                    modelMessages,
                    response.text(),
                    calls,
                    batchResult.modelResults(),
                    descriptors.stream().map(AgentToolDescriptor::modelDefinition).toList()
            ));
        }
        terminateForLimit(
                runId,
                executionOwner,
                "iteration_limit"
        );
    }

    private AgentModelResponse modelTurnWithFallback(
            List<AgentModelMessage> messages,
            List<AgentToolDescriptor> descriptors,
            DeltaWriter deltaWriter
    ) {
        long primaryStarted = System.nanoTime();
        try {
            AgentModelResponse response = modelGateway.turn(
                    request(properties.model(), messages, descriptors),
                    deltaWriter::accept,
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
            boolean retryableEmptyResponse = primaryFailure instanceof UnusableModelResponseException;
            if (deltaWriter.published()
                    || (properties.fallbackModel().equals(properties.model()) && !retryableEmptyResponse)) {
                throw primaryFailure;
            }
            deltaWriter.discard();
            long fallbackStarted = System.nanoTime();
            try {
                AgentModelResponse response = modelGateway.turn(
                        request(properties.fallbackModel(), messages, descriptors),
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
            log.warn(
                    "Agent model returned no usable content. resolvedModel={}, finishReason={}, outputTokens={}",
                    response == null ? null : response.resolvedModel(),
                    response == null ? null : response.finishReason(),
                    response == null ? null : response.usage().outputTokens()
            );
            throw new UnusableModelResponseException();
        }
    }

    private AgentModelRequest request(
            String model,
            List<AgentModelMessage> messages,
            List<AgentToolDescriptor> descriptors
    ) {
        return new AgentModelRequest(
                model,
                List.copyOf(messages),
                descriptors.stream().map(AgentToolDescriptor::modelDefinition).toList(),
                properties.temperature(),
                properties.maximumOutputTokens()
        );
    }

    private ToolBatchResult executeTools(
            AgentToolExecutionContext context,
            List<AgentModelToolCall> calls,
            UUID executionOwner,
            long deadline
    ) {
        Map<String, AgentExecutedToolCall> results = new LinkedHashMap<>();
        List<AgentModelToolCall> reads = calls.stream()
                .filter(call -> risk(call) == AgentToolRisk.READ)
                .toList();
        List<AgentModelToolCall> mutations = calls.stream()
                .filter(call -> risk(call) != AgentToolRisk.READ)
                .toList();

        List<List<AgentModelToolCall>> readGroups = new ArrayList<>(reads.stream()
                .collect(Collectors.groupingBy(
                        this::readGroupKey,
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .values());
        int maximumParallel = properties.maximumParallelReadTools();
        for (int offset = 0; offset < readGroups.size(); offset += maximumParallel) {
            requireActive(context.runId(), executionOwner, deadline);
            List<List<AgentModelToolCall>> batch =
                    readGroups.subList(offset, Math.min(readGroups.size(), offset + maximumParallel));
            List<CompletableFuture<Map<String, AgentExecutedToolCall>>> futures = batch.stream()
                    .map(group -> CompletableFuture.supplyAsync(
                            () -> executeReadGroup(context, group, executionOwner, deadline),
                            parallelReadExecutor
                    ))
                    .toList();
            for (int index = 0; index < futures.size(); index++) {
                long remainingNanos = Math.max(1, deadline - System.nanoTime());
                try {
                    results.putAll(futures.get(index).get(remainingNanos, TimeUnit.NANOSECONDS));
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
            results.put(mutation.id(), executed);
        }
        return new ToolBatchResult(
                calls.stream().map(call -> results.get(call.id()).modelResult()).toList()
        );
    }

    private Map<String, AgentExecutedToolCall> executeReadGroup(
            AgentToolExecutionContext context,
            List<AgentModelToolCall> calls,
            UUID executionOwner,
            long deadline
    ) {
        Map<String, AgentExecutedToolCall> results = new LinkedHashMap<>();
        Map<String, AgentExecutedToolCall> previousByFingerprint = new LinkedHashMap<>();
        Map<String, Integer> attemptsByFingerprint = new HashMap<>();
        for (AgentModelToolCall call : calls) {
            String callFingerprint = fingerprint(call);
            AgentExecutedToolCall previous = previousByFingerprint.get(callFingerprint);
            int attempts = attemptsByFingerprint.getOrDefault(callFingerprint, 0);
            boolean retryableFailure = previous != null
                    && !previous.successful()
                    && previous.waitingForUserMessage() == null
                    && attempts < MAXIMUM_READ_ATTEMPTS_PER_BATCH;
            if (previous == null || retryableFailure) {
                requireActive(context.runId(), executionOwner, deadline);
                previous = toolCallExecutor.execute(context, call);
                previousByFingerprint.put(callFingerprint, previous);
                attemptsByFingerprint.put(callFingerprint, attempts + 1);
            } else {
                AgentModelToolResult previousResult = previous.modelResult();
                previous = new AgentExecutedToolCall(
                        new AgentModelToolResult(call.id(), call.name(), previousResult.resultJson()),
                        previous.successful(),
                        previous.waitingForUserMessage()
                );
            }
            results.put(call.id(), previous);
        }
        return results;
    }

    private String readGroupKey(AgentModelToolCall call) {
        if (SEARCH_CATALOG_TOOL.equals(call.name())) {
            return "single-flight:" + SEARCH_CATALOG_TOOL;
        }
        return "fingerprint:" + fingerprint(call);
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
        if (text.isBlank()) {
            runService.failOwnedExecution(runId, executionOwner, "empty_model_response", null);
            return;
        }
        messageLedgerService.appendTerminalAssistant(runId, executionOwner, text, false);
    }

    private void terminateForLimit(UUID runId, UUID executionOwner, String code) {
        runService.failOwnedExecution(runId, executionOwner, code, null);
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
        if (message != null && !message.isBlank()) {
            try {
                messageLedgerService.appendAssistant(runId, executionOwner, message);
            } catch (RuntimeException ignored) {
                log.debug("Could not append an agent recovery message. runId={}", runId);
            }
        }
        try {
            runService.failOwnedExecution(runId, executionOwner, code, message);
        } catch (RuntimeException ignored) {
            log.debug("Could not persist an agent run failure. runId={}", runId);
        }
    }

    private static final class UnusableModelResponseException extends IllegalStateException {

        private UnusableModelResponseException() {
            super("Agent model returned neither text nor tool calls");
        }
    }

    private static final class ConversationLane {

        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }

    @PreDestroy
    void shutdown() {
        runExecutor.shutdownNow();
        parallelReadExecutor.shutdownNow();
        heartbeatExecutor.shutdownNow();
    }

    private record ToolBatchResult(
            List<AgentModelToolResult> modelResults
    ) {
    }

    private final class DeltaWriter {

        private static final int FLUSH_CHARACTERS = 96;
        private final UUID runId;
        private final UUID executionOwner;
        private final StringBuilder buffer = new StringBuilder();
        private boolean published;

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
            published = true;
        }

        private void discard() {
            buffer.setLength(0);
        }

        private boolean published() {
            return published;
        }

        private boolean cancelled() {
            return runService.cancellationRequested(runId, executionOwner);
        }
    }
}
