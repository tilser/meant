package com.meant.api.module.agent.service;

import com.meant.api.common.service.UserMutationExecutionLane;
import com.meant.api.module.agent.constant.AgentToolRisk;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Admits agent mutations to the shared user lane before their caller's deadline.
 *
 * <p>The caller and worker share an {@link Attempt}. Their atomic admission/cancellation
 * handshake distinguishes an action that definitely never started from one whose outcome
 * is uncertain. Reads use the same handshake but bypass the mutation lane.
 */
@Service
@RequiredArgsConstructor
public class AgentMutationExecutionLane {

    private final UserMutationExecutionLane userMutationExecutionLane;

    public <T> T execute(
            UUID userId,
            AgentToolRisk risk,
            Duration timeout,
            Supplier<T> action
    ) throws InterruptedException, TimeoutException {
        return execute(userId, risk, newAttempt(timeout), action);
    }

    public <T> T execute(
            UUID userId,
            AgentToolRisk risk,
            Attempt attempt,
            Supplier<T> action
    ) throws InterruptedException, TimeoutException {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(risk, "risk");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(action, "action");
        if (risk == AgentToolRisk.READ) {
            attempt.requireAdmission();
            return action.get();
        }

        try {
            try (UserMutationExecutionLane.Lease ignored =
                    userMutationExecutionLane.tryAcquire(userId, attempt.remainingNanos())) {
                attempt.requireAdmission();
                return action.get();
            }
        } catch (InterruptedException | TimeoutException exception) {
            attempt.cancelBeforeStart();
            throw exception;
        }
    }

    public Attempt newAttempt(Duration timeout) {
        return new Attempt(timeout);
    }

    public static final class Attempt {

        private final long createdAtNanos;
        private final long timeoutNanos;
        private State state = State.WAITING;

        private Attempt(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must not be negative");
            }
            createdAtNanos = System.nanoTime();
            long convertedTimeout;
            try {
                convertedTimeout = timeout.toNanos();
            } catch (ArithmeticException exception) {
                convertedTimeout = Long.MAX_VALUE;
            }
            timeoutNanos = convertedTimeout;
        }

        /**
         * Atomically cancels an attempt which has not been admitted.
         *
         * @return {@code true} when the action is guaranteed not to have started
         */
        public synchronized boolean cancelBeforeStart() {
            if (state == State.STARTED) {
                return false;
            }
            state = State.CANCELLED;
            return true;
        }

        public long remainingNanos() {
            long elapsed = System.nanoTime() - createdAtNanos;
            long remaining = timeoutNanos - elapsed;
            return remaining > 0 ? remaining : 0;
        }

        private synchronized void requireAdmission() throws TimeoutException {
            if (state == State.STARTED) {
                throw new IllegalStateException("Agent mutation attempt was already admitted");
            }
            if (state == State.CANCELLED || remainingNanos() == 0) {
                state = State.CANCELLED;
                throw notStarted();
            }
            state = State.STARTED;
            if (remainingNanos() == 0) {
                state = State.CANCELLED;
                throw notStarted();
            }
        }

        private TimeoutException notStarted() {
            return new TimeoutException("The action was not admitted before its deadline");
        }

        private enum State {
            WAITING,
            STARTED,
            CANCELLED
        }
    }
}
