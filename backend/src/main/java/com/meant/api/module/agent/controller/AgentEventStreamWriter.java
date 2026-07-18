package com.meant.api.module.agent.controller;

import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.controller.response.AgentRunEventResponse;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.AgentRunService;
import com.meant.api.module.agent.service.dto.AgentRunEventResult;
import com.meant.api.module.agent.service.query.GetAgentRunQuery;
import com.meant.api.module.agent.service.query.ReplayAgentRunEventsQuery;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
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
    private final ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Validates ownership and cursor retention before returning the emitter. A client disconnect only stops this
     * database follower; the durable run and its event production continue independently.
     */
    public SseEmitter open(UUID userId, UUID runId, long afterCursor) {
        var initialRun = runService.get(new GetAgentRunQuery(userId, runId));
        List<AgentRunEventResult> initialEvents = runService.replay(
                new ReplayAgentRunEventsQuery(userId, runId, afterCursor, REPLAY_PAGE_SIZE)
        );
        SseEmitter emitter = new SseEmitter(properties.eventStreamTimeout().toMillis());
        AtomicBoolean connected = new AtomicBoolean(true);
        emitter.onCompletion(() -> connected.set(false));
        emitter.onTimeout(() -> connected.set(false));
        emitter.onError(ignored -> connected.set(false));
        streamExecutor.submit(() -> follow(
                emitter,
                connected,
                userId,
                runId,
                afterCursor,
                initialRun.status(),
                initialRun.latestCursor(),
                initialEvents
        ));
        return emitter;
    }

    private void follow(
            SseEmitter emitter,
            AtomicBoolean connected,
            UUID userId,
            UUID runId,
            long afterCursor,
            AgentRunStatus initialStatus,
            long initialLatestCursor,
            List<AgentRunEventResult> initialEvents
    ) {
        long cursor = afterCursor;
        AgentRunStatus status = initialStatus;
        long latestCursor = initialLatestCursor;
        long deadline = System.nanoTime() + properties.eventStreamTimeout().toNanos();
        List<AgentRunEventResult> events = initialEvents;
        try {
            while (connected.get() && System.nanoTime() < deadline) {
                for (AgentRunEventResult event : events) {
                    if (event.cursor() <= cursor) {
                        continue;
                    }
                    send(emitter, event);
                    cursor = event.cursor();
                }
                if (events.size() == REPLAY_PAGE_SIZE) {
                    events = replay(userId, runId, cursor);
                    continue;
                }
                if (status.terminal() && cursor >= latestCursor) {
                    emitter.complete();
                    return;
                }
                sleep(properties.eventPollInterval());
                var run = runService.get(new GetAgentRunQuery(userId, runId));
                status = run.status();
                latestCursor = run.latestCursor();
                events = replay(userId, runId, cursor);
            }
            if (connected.get()) {
                emitter.complete();
            }
        } catch (IOException | IllegalStateException ignored) {
            connected.set(false);
        } catch (RuntimeException exception) {
            if (connected.get()) {
                emitter.completeWithError(exception);
            }
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

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Agent event stream interrupted", exception);
        }
    }

    @PreDestroy
    void shutdown() {
        streamExecutor.shutdownNow();
    }
}
