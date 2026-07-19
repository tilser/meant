package com.meant.api.module.agent.controller;

import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.controller.response.AgentRunEventResponse;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.AgentRunEventNotifier;
import com.meant.api.module.agent.service.AgentRunService;
import com.meant.api.module.agent.service.dto.AgentRunEventResult;
import com.meant.api.module.agent.service.query.GetAgentRunQuery;
import com.meant.api.module.agent.service.query.ReplayAgentRunEventsQuery;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@RequiredArgsConstructor
public class AgentEventStreamWriter {

    private static final int REPLAY_PAGE_SIZE = 500;

    private final AgentRunService runService;
    private final AgentProperties properties;
    private final AgentRunEventNotifier eventNotifier;
    private final AgentEventStreamAdmission streamAdmission;
    private final ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Validates ownership and cursor retention before returning the emitter. A client disconnect only stops this
     * database follower; the durable run and its event production continue independently.
     */
    public SseEmitter open(UUID userId, UUID runId, long afterCursor) {
        var initialRun = runService.get(new GetAgentRunQuery(userId, runId));
        AgentEventStreamAdmission.Permit permit = streamAdmission.acquire(userId, runId);
        AgentRunEventNotifier.Subscription subscription;
        try {
            subscription = eventNotifier.subscribe(runId, properties.eventStreamQueueSize());
        } catch (RuntimeException exception) {
            permit.close();
            throw exception;
        }
        StreamResources resources = new StreamResources(permit, subscription);
        try {
            List<AgentRunEventResult> initialEvents = runService.replay(
                    new ReplayAgentRunEventsQuery(userId, runId, afterCursor, REPLAY_PAGE_SIZE)
            );
            SseEmitter emitter = new SseEmitter(properties.eventStreamTimeout().toMillis());
            emitter.onCompletion(resources::close);
            emitter.onTimeout(resources::close);
            emitter.onError(ignored -> resources.close());
            streamExecutor.submit(() -> follow(
                    emitter,
                    resources,
                    userId,
                    runId,
                    afterCursor,
                    initialRun.status(),
                    initialEvents
            ));
            return emitter;
        } catch (RuntimeException exception) {
            resources.close();
            throw exception;
        }
    }

    private void follow(
            SseEmitter emitter,
            StreamResources resources,
            UUID userId,
            UUID runId,
            long afterCursor,
            AgentRunStatus initialStatus,
            List<AgentRunEventResult> initialEvents
    ) {
        long cursor = afterCursor;
        AgentRunStatus status = initialStatus;
        long deadline = System.nanoTime() + properties.eventStreamTimeout().toNanos();
        List<AgentRunEventResult> events = initialEvents;
        try {
            while (resources.connected() && System.nanoTime() < deadline) {
                for (AgentRunEventResult event : events) {
                    if (event.cursor() <= cursor) {
                        continue;
                    }
                    send(emitter, event);
                    cursor = event.cursor();
                    status = statusAfter(status, event.type());
                }
                if (events.size() == REPLAY_PAGE_SIZE) {
                    events = replay(userId, runId, cursor);
                    continue;
                }
                if (status.terminal()) {
                    emitter.complete();
                    return;
                }
                OptionalLong signalCursor = resources.awaitLatestCursor(properties.eventPollInterval());
                if (!resources.connected()) {
                    return;
                }
                if (signalCursor.isPresent() && signalCursor.getAsLong() <= cursor) {
                    events = List.of();
                    continue;
                }
                events = replay(userId, runId, cursor);
            }
            if (resources.connected()) {
                emitter.complete();
            }
        } catch (IOException | IllegalStateException ignored) {
            resources.close();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception) {
            if (resources.connected()) {
                emitter.completeWithError(exception);
            }
        } finally {
            resources.close();
        }
    }

    private List<AgentRunEventResult> replay(UUID userId, UUID runId, long cursor) {
        return runService.replay(new ReplayAgentRunEventsQuery(userId, runId, cursor, REPLAY_PAGE_SIZE));
    }

    private void send(SseEmitter emitter, AgentRunEventResult event) throws IOException {
        emitter.send(SseEmitter.event()
                .id(Long.toString(event.cursor()))
                .name(event.type())
                .data(AgentRunEventResponse.from(event)));
    }

    private AgentRunStatus statusAfter(AgentRunStatus current, String eventType) {
        return switch (eventType) {
            case "run.started" -> AgentRunStatus.RUNNING;
            case "run.waiting_for_user" -> AgentRunStatus.WAITING_FOR_USER;
            case "run.completed" -> AgentRunStatus.COMPLETED;
            case "run.failed" -> AgentRunStatus.FAILED;
            case "run.cancelled" -> AgentRunStatus.CANCELLED;
            default -> current;
        };
    }

    @PreDestroy
    void shutdown() {
        streamExecutor.shutdownNow();
    }

    private static final class StreamResources implements AutoCloseable {

        private final AgentEventStreamAdmission.Permit permit;
        private final AgentRunEventNotifier.Subscription subscription;
        private final AtomicBoolean closed = new AtomicBoolean();

        private StreamResources(
                AgentEventStreamAdmission.Permit permit,
                AgentRunEventNotifier.Subscription subscription
        ) {
            this.permit = permit;
            this.subscription = subscription;
        }

        private boolean connected() {
            return !closed.get();
        }

        private OptionalLong awaitLatestCursor(java.time.Duration timeout) throws InterruptedException {
            return subscription.awaitLatestCursor(timeout);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            subscription.close();
            permit.close();
        }
    }
}
