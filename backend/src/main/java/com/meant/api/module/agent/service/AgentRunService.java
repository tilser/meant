package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentRunEventType;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.entity.AgentRunEvent;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentRunEventRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.service.command.CancelAgentRunCommand;
import com.meant.api.module.agent.service.dto.AgentEventPayload;
import com.meant.api.module.agent.service.dto.AgentModelUsage;
import com.meant.api.module.agent.service.dto.AgentRunEventResult;
import com.meant.api.module.agent.service.dto.AgentRunResult;
import com.meant.api.module.agent.service.query.GetAgentRunQuery;
import com.meant.api.module.agent.service.query.ReplayAgentRunEventsQuery;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class AgentRunService {

    private static final int EVENT_SCHEMA_VERSION = 1;

    private final AgentRunRepository runRepository;
    private final AgentRunEventRepository eventRepository;
    private final AgentJsonSupport jsonSupport;
    private final AgentMetrics metrics;
    private final AgentRunEventNotifier eventNotifier;
    private final AgentProperties properties;
    private final Clock clock;

    @Transactional
    public Optional<UUID> claim(UUID runId) {
        AgentRun run = runRepository.findForUpdate(runId).orElseThrow(AgentException::notFound);
        if (run.getStatus() != AgentRunStatus.QUEUED) {
            return Optional.empty();
        }
        if (runRepository.existsByUserIdAndStatusAndIdNot(
                run.getUserId(), AgentRunStatus.RUNNING, run.getId())) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (run.isCancellationRequested()) {
            run.cancel(now);
            appendLocked(run, AgentRunEventType.RUN_CANCELLED, AgentEventPayload.text("Run cancelled."), now);
            metrics.run(AgentRunStatus.CANCELLED, run.getModel(), null);
            return Optional.empty();
        }
        boolean restarted = run.getStartedAt() != null;
        UUID executionOwner = UUID.randomUUID();
        run.claim(executionOwner, now, leaseExpiry(now));
        String message = restarted
                ? "Agent run restarted after worker recovery."
                : "Agent run started.";
        appendLocked(run, AgentRunEventType.RUN_STARTED, AgentEventPayload.text(message), now);
        metrics.run(AgentRunStatus.RUNNING, run.getModel(), null);
        return Optional.of(executionOwner);
    }

    @Transactional
    public boolean renewLease(UUID runId, UUID executionOwner) {
        AgentRun run = runRepository.findForUpdate(runId).orElse(null);
        if (run == null) {
            return false;
        }
        Instant now = clock.instant();
        return run.renewLease(executionOwner, now, leaseExpiry(now));
    }

    @Transactional
    public boolean expireLease(UUID runId) {
        AgentRun run = runRepository.findForUpdate(runId).orElse(null);
        if (run == null) {
            return false;
        }
        Instant now = clock.instant();
        if (!run.leaseExpiredAt(now)) {
            return false;
        }
        if (run.isCancellationRequested()) {
            run.cancel(now);
            appendLocked(run, AgentRunEventType.RUN_CANCELLED, AgentEventPayload.text("Run cancelled."), now);
            metrics.run(AgentRunStatus.CANCELLED, run.getModel(), "execution_lease_expired");
        } else {
            run.requeueAfterLeaseExpiry(now);
            metrics.run(AgentRunStatus.QUEUED, run.getModel(), "execution_lease_expired");
        }
        return true;
    }

    @Transactional
    public void recordIteration(UUID runId, AgentModelUsage usage) {
        AgentRun run = runRepository.findForUpdate(runId).orElseThrow(AgentException::notFound);
        Instant now = clock.instant();
        if (!run.leaseActiveAt(now)) {
            return;
        }
        run.recordIteration();
        if (usage != null) {
            run.addUsage(usage.inputTokens(), usage.outputTokens());
            metrics.modelUsage(run.getModel(), usage.inputTokens(), usage.outputTokens());
        }
    }

    @Transactional
    public void recordIteration(UUID runId, UUID executionOwner, AgentModelUsage usage) {
        AgentRun run = runningForUpdate(runId, executionOwner);
        run.recordIteration();
        if (usage != null) {
            run.addUsage(usage.inputTokens(), usage.outputTokens());
            metrics.modelUsage(run.getModel(), usage.inputTokens(), usage.outputTokens());
        }
    }

    @Transactional
    public AgentRunEventResult append(UUID runId, AgentRunEventType type, AgentEventPayload payload) {
        AgentRun run = runningForUpdate(runId);
        return AgentResultMapper.event(appendLocked(run, type, payload, clock.instant()));
    }

    @Transactional
    public AgentRunEventResult append(
            UUID runId,
            UUID executionOwner,
            AgentRunEventType type,
            AgentEventPayload payload
    ) {
        AgentRun run = runningForUpdate(runId, executionOwner);
        return AgentResultMapper.event(appendLocked(run, type, payload, clock.instant()));
    }

    @Transactional
    public void complete(UUID runId) {
        AgentRun run = runningForUpdate(runId);
        completeLocked(run);
    }

    @Transactional
    public void complete(UUID runId, UUID executionOwner) {
        AgentRun run = runningForUpdate(runId, executionOwner);
        completeLocked(run);
    }

    private void completeLocked(AgentRun run) {
        Instant now = clock.instant();
        if (run.isCancellationRequested()) {
            run.cancel(now);
            appendLocked(run, AgentRunEventType.RUN_CANCELLED, AgentEventPayload.text("Run cancelled."), now);
            metrics.run(AgentRunStatus.CANCELLED, run.getModel(), null);
            return;
        }
        run.complete(now);
        appendLocked(run, AgentRunEventType.RUN_COMPLETED, AgentEventPayload.text("Run completed."), now);
        metrics.run(AgentRunStatus.COMPLETED, run.getModel(), null);
    }

    @Transactional
    public void waitForUser(UUID runId, String message) {
        AgentRun run = runningForUpdate(runId);
        waitForUserLocked(run, message);
    }

    @Transactional
    public void waitForUser(UUID runId, UUID executionOwner, String message) {
        AgentRun run = runningForUpdate(runId, executionOwner);
        waitForUserLocked(run, message);
    }

    private void waitForUserLocked(AgentRun run, String message) {
        Instant now = clock.instant();
        if (run.isCancellationRequested()) {
            run.cancel(now);
            appendLocked(run, AgentRunEventType.RUN_CANCELLED, AgentEventPayload.text("Run cancelled."), now);
            metrics.run(AgentRunStatus.CANCELLED, run.getModel(), null);
            return;
        }
        run.waitForUser(message, now);
        appendLocked(run, AgentRunEventType.RUN_WAITING_FOR_USER, AgentEventPayload.text(message), now);
        metrics.clarification(run.getModel());
        metrics.run(AgentRunStatus.WAITING_FOR_USER, run.getModel(), null);
    }

    @Transactional
    public void fail(UUID runId, String code, String message) {
        AgentRun run = runRepository.findForUpdate(runId).orElseThrow(AgentException::notFound);
        if (run.getStatus().terminal()) {
            return;
        }
        Instant now = clock.instant();
        run.fail(code, message, now);
        appendLocked(run, AgentRunEventType.RUN_FAILED, AgentEventPayload.failure(code, message), now);
        metrics.run(AgentRunStatus.FAILED, run.getModel(), code);
    }

    @Transactional
    public boolean failOwnedExecution(UUID runId, UUID executionOwner, String code, String message) {
        AgentRun run = runRepository.findForUpdate(runId).orElseThrow(AgentException::notFound);
        Instant now = clock.instant();
        if (!run.leaseOwnedBy(executionOwner, now)) {
            return false;
        }
        run.fail(code, message, now);
        appendLocked(run, AgentRunEventType.RUN_FAILED, AgentEventPayload.failure(code, message), now);
        metrics.run(AgentRunStatus.FAILED, run.getModel(), code);
        return true;
    }

    @Transactional
    public void cancel(UUID runId) {
        AgentRun run = runRepository.findForUpdate(runId).orElseThrow(AgentException::notFound);
        if (run.getStatus().terminal()) {
            return;
        }
        Instant now = clock.instant();
        run.cancel(now);
        appendLocked(run, AgentRunEventType.RUN_CANCELLED, AgentEventPayload.text("Run cancelled."), now);
        metrics.run(AgentRunStatus.CANCELLED, run.getModel(), null);
    }

    @Transactional
    public boolean cancelOwnedExecution(UUID runId, UUID executionOwner) {
        AgentRun run = runRepository.findForUpdate(runId).orElseThrow(AgentException::notFound);
        Instant now = clock.instant();
        if (!run.leaseOwnedBy(executionOwner, now)) {
            return false;
        }
        run.cancel(now);
        appendLocked(run, AgentRunEventType.RUN_CANCELLED, AgentEventPayload.text("Run cancelled."), now);
        metrics.run(AgentRunStatus.CANCELLED, run.getModel(), null);
        return true;
    }

    @Transactional
    public AgentRunResult requestCancellation(@Valid CancelAgentRunCommand command) {
        AgentRun run = runRepository.findOwnedForUpdate(command.runId(), command.userId())
                .orElseThrow(AgentException::notFound);
        if (run.getStatus() == AgentRunStatus.QUEUED) {
            Instant now = clock.instant();
            run.cancel(now);
            appendLocked(run, AgentRunEventType.RUN_CANCELLED, AgentEventPayload.text("Run cancelled."), now);
            metrics.run(AgentRunStatus.CANCELLED, run.getModel(), null);
        } else {
            run.requestCancellation();
        }
        return AgentResultMapper.run(run);
    }

    @Transactional(readOnly = true)
    public boolean cancellationRequested(UUID runId) {
        return runRepository.findById(runId)
                .map(run -> run.getStatus() != AgentRunStatus.RUNNING
                        || run.isCancellationRequested()
                        || !run.leaseActiveAt(clock.instant()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean cancellationRequested(UUID runId, UUID executionOwner) {
        return runRepository.findById(runId)
                .map(run -> run.isCancellationRequested()
                        || !run.leaseOwnedBy(executionOwner, clock.instant()))
                .orElse(true);
    }

    @Transactional
    public void requireOwnedExecution(UUID runId, UUID executionOwner) {
        runningForUpdate(runId, executionOwner);
    }

    @Transactional(readOnly = true)
    public AgentRunResult get(@Valid GetAgentRunQuery query) {
        return runRepository.findByIdAndUserId(query.runId(), query.userId())
                .map(AgentResultMapper::run)
                .orElseThrow(AgentException::notFound);
    }

    @Transactional(readOnly = true)
    public List<AgentRunEventResult> replay(@Valid ReplayAgentRunEventsQuery query) {
        AgentRun run = runRepository.findByIdAndUserId(query.runId(), query.userId())
                .orElseThrow(AgentException::notFound);
        var earliest = eventRepository.findFirstByRunIdOrderByCursorAsc(run.getId());
        if (earliest.isPresent() && query.afterCursor() + 1 < earliest.get().getCursor()) {
            throw AgentException.cursorExpired();
        }
        if (earliest.isEmpty() && query.afterCursor() < run.getLastEventCursor()) {
            throw AgentException.cursorExpired();
        }
        return eventRepository.findByRunIdAndCursorGreaterThanOrderByCursorAsc(
                        run.getId(),
                        query.afterCursor(),
                        PageRequest.of(0, query.limit())
                ).stream()
                .map(AgentResultMapper::event)
                .toList();
    }

    private AgentRun runningForUpdate(UUID runId) {
        AgentRun run = runRepository.findForUpdate(runId).orElseThrow(AgentException::notFound);
        if (!run.leaseActiveAt(clock.instant())) {
            throw AgentException.conflict("The agent run is not active.");
        }
        return run;
    }

    private AgentRun runningForUpdate(UUID runId, UUID executionOwner) {
        AgentRun run = runRepository.findForUpdate(runId).orElseThrow(AgentException::notFound);
        if (!run.leaseOwnedBy(executionOwner, clock.instant())) {
            throw AgentException.conflict("The agent run is not active.");
        }
        return run;
    }

    private Instant leaseExpiry(Instant now) {
        return now.plus(properties.staleRunAge());
    }

    private AgentRunEvent appendLocked(
            AgentRun run,
            AgentRunEventType type,
            AgentEventPayload payload,
            Instant now
    ) {
        AgentRunEvent event = AgentRunEvent.builder()
                .runId(run.getId())
                .conversationId(run.getConversationId())
                .cursor(run.nextEventCursor())
                .eventType(type)
                .schemaVersion(EVENT_SCHEMA_VERSION)
                .payloadJson(jsonSupport.writeArtifact(payload))
                .occurredAt(now)
                .build();
        AgentRunEvent stored = eventRepository.save(event);
        eventNotifier.signalAfterCommit(run.getId(), stored.getCursor());
        return stored;
    }
}
