package com.meant.api.module.agent.controller;

import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentStreamAdmissionProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentEventStreamAdmission {

    private final AgentStreamAdmissionProperties properties;
    private final Map<UUID, Integer> streamsByUser = new HashMap<>();
    private final Map<UserRun, Integer> streamsByRun = new HashMap<>();

    public synchronized Permit acquire(UUID userId, UUID runId) {
        UserRun userRun = new UserRun(userId, runId);
        if (streamsByUser.getOrDefault(userId, 0) >= properties.maximumPerUser()
                || streamsByRun.getOrDefault(userRun, 0) >= properties.maximumPerRun()) {
            throw AgentException.streamLimit();
        }
        streamsByUser.merge(userId, 1, Integer::sum);
        streamsByRun.merge(userRun, 1, Integer::sum);
        return new Permit(this, userRun);
    }

    private synchronized void release(UserRun userRun) {
        decrement(streamsByUser, userRun.userId());
        decrement(streamsByRun, userRun);
    }

    private <K> void decrement(Map<K, Integer> counts, K key) {
        counts.computeIfPresent(key, (ignored, count) -> count == 1 ? null : count - 1);
    }

    synchronized int activeStreams(UUID userId) {
        return streamsByUser.getOrDefault(userId, 0);
    }

    synchronized int activeStreams(UUID userId, UUID runId) {
        return streamsByRun.getOrDefault(new UserRun(userId, runId), 0);
    }

    synchronized int trackedUsers() {
        return streamsByUser.size();
    }

    synchronized int trackedRuns() {
        return streamsByRun.size();
    }

    public static final class Permit implements AutoCloseable {

        private final AgentEventStreamAdmission owner;
        private final UserRun userRun;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Permit(AgentEventStreamAdmission owner, UserRun userRun) {
            this.owner = owner;
            this.userRun = userRun;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                owner.release(userRun);
            }
        }
    }

    private record UserRun(UUID userId, UUID runId) {
    }
}
