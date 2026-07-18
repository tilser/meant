package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentRunEventType;
import com.meant.api.module.agent.constant.AgentToolInvocationStatus;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.entity.AgentToolInvocation;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.repository.AgentToolInvocationRepository;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.dto.AgentEventPayload;
import com.meant.api.module.agent.service.dto.AgentMessageResult;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.AgentToolInvocationReservation;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentToolInvocationService {

    private final AgentToolInvocationRepository invocationRepository;
    private final AgentArtifactReferenceRepository artifactRepository;
    private final AgentRunRepository runRepository;
    private final AgentRunService runService;
    private final AgentMessageLedgerService messageLedgerService;
    private final AgentArtifactService artifactService;
    private final AgentJsonSupport jsonSupport;
    private final AgentMetrics metrics;
    private final AgentProperties properties;
    private final Clock clock;

    @Transactional
    public void rejectUnregistered(UUID runId, AgentModelToolCall call, String toolVersion) {
        rejectUnregistered(runId, call, toolVersion, null);
    }

    @Transactional
    public void rejectUnregistered(
            UUID runId,
            AgentModelToolCall call,
            String toolVersion,
            UUID executionOwner
    ) {
        requireOwnedExecution(runId, executionOwner);
        AgentRun run = runRepository.findForUpdate(runId).orElseThrow(AgentException::notFound);
        run.recordToolInvocation();
        Instant now = clock.instant();
        AgentToolInvocation invocation = invocationRepository.save(AgentToolInvocation.builder()
                .runId(runId)
                .modelToolCallId(call.id())
                .toolName(call.name())
                .toolVersion(toolVersion)
                .riskClass(com.meant.api.module.agent.constant.AgentToolRisk.READ)
                .status(AgentToolInvocationStatus.PROPOSED)
                .argumentsJson(jsonSupport.bounded(call.argumentsJson()))
                .idempotencyKey("agent:rejected:" + runId + ":" + call.id())
                .createdAt(now)
                .build());
        appendEvent(
                runId,
                executionOwner,
                AgentRunEventType.TOOL_PROPOSED,
                AgentEventPayload.tool(call.id(), call.name(), "Tool proposed.", null)
        );
        invocation.fail(
                AgentToolInvocationStatus.REJECTED,
                "tool_not_allowed",
                "That tool is not available.",
                0,
                now
        );
        appendEvent(
                runId,
                executionOwner,
                AgentRunEventType.TOOL_FAILED,
                AgentEventPayload.tool(call.id(), call.name(), "That tool is not available.", null)
        );
        metrics.tool("unregistered", null, "rejected", 0);
    }

    @Transactional
    public void rejectInvalid(
            UUID runId,
            AgentModelToolCall call,
            AgentToolDescriptor descriptor,
            String rawArgumentsJson
    ) {
        rejectInvalid(runId, call, descriptor, rawArgumentsJson, null);
    }

    @Transactional
    public void rejectInvalid(
            UUID runId,
            AgentModelToolCall call,
            AgentToolDescriptor descriptor,
            String rawArgumentsJson,
            UUID executionOwner
    ) {
        requireOwnedExecution(runId, executionOwner);
        AgentRun run = runRepository.findForUpdate(runId).orElseThrow(AgentException::notFound);
        run.recordToolInvocation();
        Instant now = clock.instant();
        AgentToolInvocation invocation = invocationRepository.save(AgentToolInvocation.builder()
                .runId(runId)
                .modelToolCallId(call.id())
                .toolName(descriptor.name())
                .toolVersion(descriptor.version())
                .riskClass(descriptor.riskClass())
                .status(AgentToolInvocationStatus.PROPOSED)
                .argumentsJson(jsonSupport.bounded(rawArgumentsJson == null ? "" : rawArgumentsJson))
                .idempotencyKey("agent:invalid:" + runId + ":" + call.id())
                .createdAt(now)
                .build());
        appendEvent(
                runId,
                executionOwner,
                AgentRunEventType.TOOL_PROPOSED,
                AgentEventPayload.tool(call.id(), descriptor.name(), "Tool proposed.", null)
        );
        invocation.fail(
                AgentToolInvocationStatus.REJECTED,
                "invalid_arguments",
                "The tool arguments were invalid.",
                0,
                now
        );
        appendEvent(
                runId,
                executionOwner,
                AgentRunEventType.TOOL_FAILED,
                AgentEventPayload.tool(
                        call.id(),
                        descriptor.name(),
                        "The tool arguments were invalid.",
                        null
                )
        );
        metrics.tool(descriptor.name(), descriptor.riskClass(), "rejected", 0);
    }

    @Transactional
    public void rejectUnauthorized(
            UUID runId,
            AgentModelToolCall call,
            AgentToolDescriptor descriptor
    ) {
        rejectUnauthorized(runId, call, descriptor, null);
    }

    @Transactional
    public void rejectUnauthorized(
            UUID runId,
            AgentModelToolCall call,
            AgentToolDescriptor descriptor,
            UUID executionOwner
    ) {
        requireOwnedExecution(runId, executionOwner);
        AgentRun run = runRepository.findForUpdate(runId).orElseThrow(AgentException::notFound);
        run.recordToolInvocation();
        Instant now = clock.instant();
        AgentToolInvocation invocation = invocationRepository.save(AgentToolInvocation.builder()
                .runId(runId)
                .modelToolCallId(call.id())
                .toolName(descriptor.name())
                .toolVersion(descriptor.version())
                .riskClass(descriptor.riskClass())
                .status(AgentToolInvocationStatus.PROPOSED)
                .argumentsJson(jsonSupport.bounded(call.argumentsJson()))
                .idempotencyKey("agent:unauthorized:" + runId + ":" + call.id())
                .createdAt(now)
                .build());
        appendEvent(
                runId,
                executionOwner,
                AgentRunEventType.TOOL_PROPOSED,
                AgentEventPayload.tool(call.id(), descriptor.name(), "Tool proposed.", null)
        );
        invocation.fail(
                AgentToolInvocationStatus.REJECTED,
                "authorization_required",
                "That action needs a clearer instruction from the user.",
                0,
                now
        );
        appendEvent(
                runId,
                executionOwner,
                AgentRunEventType.TOOL_FAILED,
                AgentEventPayload.tool(
                        call.id(),
                        descriptor.name(),
                        "That action needs a clearer instruction from the user.",
                        null
                )
        );
        metrics.tool(descriptor.name(), descriptor.riskClass(), "rejected", 0);
    }

    @Transactional
    public AgentToolInvocationReservation reserve(
            UUID runId,
            AgentModelToolCall call,
            AgentToolDescriptor descriptor,
            String argumentsJson,
            String idempotencyKey
    ) {
        return reserve(runId, call, descriptor, argumentsJson, idempotencyKey, null);
    }

    @Transactional
    public AgentToolInvocationReservation reserve(
            UUID runId,
            AgentModelToolCall call,
            AgentToolDescriptor descriptor,
            String argumentsJson,
            String idempotencyKey,
            UUID executionOwner
    ) {
        requireOwnedExecution(runId, executionOwner);
        AgentRun run = runRepository.findForUpdate(runId).orElseThrow(AgentException::notFound);
        String boundedArguments = jsonSupport.bounded(argumentsJson);
        var existing = invocationRepository.findByIdempotencyKeyForUpdate(idempotencyKey);
        if (existing.isPresent()) {
            AgentToolInvocation invocation = existing.get();
            requireMatchingInvocation(invocation, runId, descriptor, boundedArguments);
            recoverStaleMutation(invocation);
            if (invocation.getStatus() == AgentToolInvocationStatus.COMPLETED) {
                return new AgentToolInvocationReservation(
                        invocation.getId(),
                        false,
                        invocation.getResultJson(),
                        artifactRepository.findByToolInvocationIdOrderByOrdinalAsc(invocation.getId()).stream()
                                .map(AgentResultMapper::artifact)
                                .toList(),
                        false
                );
            }
            if (invocation.getStatus() == AgentToolInvocationStatus.UNCERTAIN
                    || (invocation.getStatus() == AgentToolInvocationStatus.FAILED
                    && invocation.getRiskClass() == AgentToolRisk.READ)) {
                invocation.retry();
                run.recordToolInvocation();
                appendProposed(runId, executionOwner, call, descriptor);
                return new AgentToolInvocationReservation(
                        invocation.getId(),
                        true,
                        null,
                        java.util.List.of(),
                        invocation.getRiskClass() != AgentToolRisk.READ
                );
            }
            throw AgentException.conflict("An equivalent tool mutation is already in progress or cannot be retried.");
        }
        run.recordToolInvocation();
        AgentToolInvocation invocation = invocationRepository.save(AgentToolInvocation.builder()
                .runId(runId)
                .modelToolCallId(call.id())
                .toolName(descriptor.name())
                .toolVersion(descriptor.version())
                .riskClass(descriptor.riskClass())
                .status(AgentToolInvocationStatus.PROPOSED)
                .argumentsJson(boundedArguments)
                .idempotencyKey(idempotencyKey)
                .createdAt(clock.instant())
                .build());
        appendProposed(runId, executionOwner, call, descriptor);
        return new AgentToolInvocationReservation(invocation.getId(), true, null, java.util.List.of(), false);
    }

    private void requireMatchingInvocation(
            AgentToolInvocation invocation,
            UUID runId,
            AgentToolDescriptor descriptor,
            String boundedArguments
    ) {
        if (!invocation.getRunId().equals(runId)
                || !invocation.getToolName().equals(descriptor.name())
                || !invocation.getToolVersion().equals(descriptor.version())
                || !invocation.getArgumentsJson().equals(boundedArguments)) {
            throw AgentException.conflict(
                    "The tool idempotency key was already used for a different tool contract or arguments.");
        }
    }

    private void recoverStaleMutation(AgentToolInvocation invocation) {
        Instant now = clock.instant();
        if (invocation.getStatus() == AgentToolInvocationStatus.RUNNING
                && invocation.getRiskClass() != AgentToolRisk.READ
                && invocation.getStartedAt() != null
                && invocation.getStartedAt().isBefore(
                        AgentMutationRecoveryService.staleCutoff(now, properties.toolDeadline()))) {
            invocation.fail(
                    AgentToolInvocationStatus.UNCERTAIN,
                    AgentMutationRecoveryService.STALE_CLASSIFICATION,
                    AgentMutationRecoveryService.STALE_MUTATION_MESSAGE,
                    0,
                    now
            );
        }
    }

    private void appendProposed(
            UUID runId,
            UUID executionOwner,
            AgentModelToolCall call,
            AgentToolDescriptor descriptor
    ) {
        appendEvent(
                runId,
                executionOwner,
                AgentRunEventType.TOOL_PROPOSED,
                AgentEventPayload.tool(call.id(), descriptor.name(), "Tool proposed.", null)
        );
    }

    @Transactional
    public void start(UUID runId, UUID invocationId, String modelToolCallId, String toolName) {
        start(runId, invocationId, modelToolCallId, toolName, null);
    }

    @Transactional
    public void start(
            UUID runId,
            UUID invocationId,
            String modelToolCallId,
            String toolName,
            UUID executionOwner
    ) {
        requireOwnedExecution(runId, executionOwner);
        AgentToolInvocation invocation = invocationRepository.findById(invocationId)
                .orElseThrow(AgentException::notFound);
        invocation.start(clock.instant());
        appendEvent(
                runId,
                executionOwner,
                AgentRunEventType.TOOL_STARTED,
                AgentEventPayload.tool(modelToolCallId, toolName, "Tool started.", null)
        );
    }

    @Transactional
    public void complete(
            UUID runId,
            UUID conversationId,
            UUID invocationId,
            String modelToolCallId,
            String toolName,
            AgentToolExecutionResult result,
            long latencyMilliseconds
    ) {
        complete(
                runId,
                conversationId,
                invocationId,
                modelToolCallId,
                toolName,
                result,
                latencyMilliseconds,
                null
        );
    }

    @Transactional
    public void complete(
            UUID runId,
            UUID conversationId,
            UUID invocationId,
            String modelToolCallId,
            String toolName,
            AgentToolExecutionResult result,
            long latencyMilliseconds,
            UUID executionOwner
    ) {
        requireOwnedExecution(runId, executionOwner);
        AgentToolInvocation invocation = invocationRepository.findById(invocationId)
                .orElseThrow(AgentException::notFound);
        var first = result.artifacts().stream().findFirst().orElse(null);
        invocation.complete(
                jsonSupport.bounded(result.resultJson()),
                latencyMilliseconds,
                first == null ? null : first.canonicalProductKey(),
                first == null ? null : first.offerKey(),
                first == null ? null : first.inventoryItemId(),
                first == null ? null : first.cartId(),
                first == null ? null : first.cartLineId(),
                first == null ? null : first.checkoutAttemptId(),
                clock.instant()
        );
        AgentMessageResult message = messageLedgerService.appendToolResult(
                runId,
                executionOwner,
                modelToolCallId,
                toolName,
                jsonSupport.bounded(result.resultJson())
        );
        artifactService.persist(
                conversationId,
                runId,
                executionOwner,
                message.messageId(),
                invocationId,
                result.artifacts()
        );
        appendEvent(
                runId,
                executionOwner,
                AgentRunEventType.TOOL_COMPLETED,
                AgentEventPayload.tool(modelToolCallId, toolName, result.safeSummary(), result.resultJson())
        );
        if (result.domainEventType() != null) {
            appendEvent(
                    runId,
                    executionOwner,
                    result.domainEventType(),
                    AgentEventPayload.tool(modelToolCallId, toolName, result.safeSummary(), result.resultJson())
            );
        }
        metrics.tool(toolName, invocation.getRiskClass(), "completed", latencyMilliseconds);
    }

    @Transactional
    public void fail(
            UUID runId,
            UUID invocationId,
            String modelToolCallId,
            String toolName,
            AgentToolInvocationStatus status,
            String classification,
            String safeMessage,
            long latencyMilliseconds
    ) {
        fail(
                runId,
                invocationId,
                modelToolCallId,
                toolName,
                status,
                classification,
                safeMessage,
                latencyMilliseconds,
                null
        );
    }

    @Transactional
    public void fail(
            UUID runId,
            UUID invocationId,
            String modelToolCallId,
            String toolName,
            AgentToolInvocationStatus status,
            String classification,
            String safeMessage,
            long latencyMilliseconds,
            UUID executionOwner
    ) {
        requireOwnedExecution(runId, executionOwner);
        AgentToolInvocation invocation = invocationRepository.findById(invocationId)
                .orElseThrow(AgentException::notFound);
        invocation.fail(status, classification, safeMessage, latencyMilliseconds, clock.instant());
        appendEvent(
                runId,
                executionOwner,
                AgentRunEventType.TOOL_FAILED,
                AgentEventPayload.tool(modelToolCallId, toolName, safeMessage, null)
        );
        metrics.tool(toolName, invocation.getRiskClass(), status.name(), latencyMilliseconds);
    }

    private void requireOwnedExecution(UUID runId, UUID executionOwner) {
        if (executionOwner != null) {
            runService.requireOwnedExecution(runId, executionOwner);
        }
    }

    private void appendEvent(
            UUID runId,
            UUID executionOwner,
            AgentRunEventType type,
            AgentEventPayload payload
    ) {
        if (executionOwner == null) {
            runService.append(runId, type, payload);
            return;
        }
        runService.append(runId, executionOwner, type, payload);
    }
}
