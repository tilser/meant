package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.common.service.UserMutationExecutionLane;
import com.meant.api.module.agent.constant.AgentToolRisk;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentMutationExecutionLaneTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void serializesMutationsForTheSameUser() throws Exception {
        AgentMutationExecutionLane lane = lane();
        UUID userId = UUID.randomUUID();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        Future<?> first = executor.submit(() -> lane.execute(
                userId,
                AgentToolRisk.REVERSIBLE_MUTATION,
                Duration.ofSeconds(2),
                () -> {
                    firstEntered.countDown();
                    await(releaseFirst);
                    return null;
                }
        ));
        assertThat(firstEntered.await(1, TimeUnit.SECONDS)).isTrue();

        Future<?> second = executor.submit(() -> lane.execute(
                userId,
                AgentToolRisk.IRREVERSIBLE_MUTATION,
                Duration.ofSeconds(2),
                () -> {
                    secondEntered.countDown();
                    return null;
                }
        ));

        assertThat(secondEntered.await(50, TimeUnit.MILLISECONDS)).isFalse();
        releaseFirst.countDown();
        first.get(1, TimeUnit.SECONDS);
        second.get(1, TimeUnit.SECONDS);
        assertThat(secondEntered.getCount()).isZero();
    }

    @Test
    void differentUsersAndReadsDoNotWaitForAnExistingMutation() throws Exception {
        AgentMutationExecutionLane lane = lane();
        UUID blockedUserId = UUID.randomUUID();
        CountDownLatch mutationEntered = new CountDownLatch(1);
        CountDownLatch releaseMutation = new CountDownLatch(1);
        Future<?> blockedMutation = executor.submit(() -> lane.execute(
                blockedUserId,
                AgentToolRisk.REVERSIBLE_MUTATION,
                Duration.ofSeconds(2),
                () -> {
                    mutationEntered.countDown();
                    await(releaseMutation);
                    return null;
                }
        ));
        assertThat(mutationEntered.await(1, TimeUnit.SECONDS)).isTrue();

        Future<String> otherUserMutation = executor.submit(() -> lane.execute(
                UUID.randomUUID(),
                AgentToolRisk.REVERSIBLE_MUTATION,
                Duration.ofSeconds(1),
                () -> "other-user"
        ));
        Future<String> sameUserRead = executor.submit(() -> lane.execute(
                blockedUserId,
                AgentToolRisk.READ,
                Duration.ofSeconds(1),
                () -> "read"
        ));

        assertThat(otherUserMutation.get(1, TimeUnit.SECONDS)).isEqualTo("other-user");
        assertThat(sameUserRead.get(1, TimeUnit.SECONDS)).isEqualTo("read");
        releaseMutation.countDown();
        blockedMutation.get(1, TimeUnit.SECONDS);
    }

    @Test
    void mutationWaitIsBoundedWithoutExecutingTheAction() throws Exception {
        AgentMutationExecutionLane lane = lane();
        UUID userId = UUID.randomUUID();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean();
        Future<?> first = executor.submit(() -> lane.execute(
                userId,
                AgentToolRisk.REVERSIBLE_MUTATION,
                Duration.ofSeconds(2),
                () -> {
                    firstEntered.countDown();
                    await(releaseFirst);
                    return null;
                }
        ));
        assertThat(firstEntered.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> lane.execute(
                userId,
                AgentToolRisk.REVERSIBLE_MUTATION,
                Duration.ofMillis(20),
                () -> {
                    executed.set(true);
                    return null;
                }
        )).isInstanceOf(TimeoutException.class);
        assertThat(executed).isFalse();

        releaseFirst.countDown();
        first.get(1, TimeUnit.SECONDS);
    }

    @Test
    void failedMutationAlwaysReleasesTheLane() throws Exception {
        AgentMutationExecutionLane lane = lane();
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> lane.execute(
                userId,
                AgentToolRisk.REVERSIBLE_MUTATION,
                Duration.ofSeconds(1),
                () -> {
                    throw new IllegalStateException("provider failed");
                }
        )).isInstanceOf(IllegalStateException.class);

        assertThat(lane.execute(
                userId,
                AgentToolRisk.CHECKOUT_PREPARATION,
                Duration.ofSeconds(1),
                () -> "next"
        )).isEqualTo("next");
    }

    @Test
    void interruptedWaiterCannotExecuteAfterTheLaneIsReleased() throws Exception {
        AgentMutationExecutionLane lane = lane();
        UUID userId = UUID.randomUUID();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        Future<?> first = executor.submit(() -> lane.execute(
                userId,
                AgentToolRisk.REVERSIBLE_MUTATION,
                Duration.ofSeconds(2),
                () -> {
                    firstEntered.countDown();
                    await(releaseFirst);
                    return null;
                }
        ));
        assertThat(firstEntered.await(1, TimeUnit.SECONDS)).isTrue();

        AtomicBoolean executed = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch waiterStarted = new CountDownLatch(1);
        Thread waiter = Thread.ofVirtual().start(() -> {
            waiterStarted.countDown();
            try {
                lane.execute(
                        userId,
                        AgentToolRisk.REVERSIBLE_MUTATION,
                        Duration.ofSeconds(10),
                        () -> {
                            executed.set(true);
                            return null;
                        }
                );
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        assertThat(waiterStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(awaitParked(waiter, Duration.ofSeconds(1))).isTrue();

        waiter.interrupt();
        waiter.join(1000);
        assertThat(waiter.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOf(InterruptedException.class);
        releaseFirst.countDown();
        first.get(1, TimeUnit.SECONDS);
        assertThat(executed).isFalse();
    }

    @Test
    void callerCanAtomicallyCancelAWaitingAttemptBeforeInterruptingItsWorker() throws Exception {
        AgentMutationExecutionLane lane = lane();
        UUID userId = UUID.randomUUID();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        Future<?> first = executor.submit(() -> lane.execute(
                userId,
                AgentToolRisk.REVERSIBLE_MUTATION,
                Duration.ofSeconds(2),
                () -> {
                    firstEntered.countDown();
                    await(releaseFirst);
                    return null;
                }
        ));
        assertThat(firstEntered.await(1, TimeUnit.SECONDS)).isTrue();

        AgentMutationExecutionLane.Attempt attempt = lane.newAttempt(Duration.ofSeconds(10));
        AtomicBoolean executed = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch waiterStarted = new CountDownLatch(1);
        Thread waiter = Thread.ofVirtual().start(() -> {
            waiterStarted.countDown();
            try {
                lane.execute(
                        userId,
                        AgentToolRisk.REVERSIBLE_MUTATION,
                        attempt,
                        () -> {
                            executed.set(true);
                            return null;
                        }
                );
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        assertThat(waiterStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(awaitParked(waiter, Duration.ofSeconds(1))).isTrue();

        assertThat(attempt.cancelBeforeStart()).isTrue();
        waiter.interrupt();
        waiter.join(1000);
        releaseFirst.countDown();
        first.get(1, TimeUnit.SECONDS);

        assertThat(waiter.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOf(InterruptedException.class);
        assertThat(executed).isFalse();
    }

    @Test
    void cartAndAgentMutationsUseTheSameUserLane() throws Exception {
        UserMutationExecutionLane sharedLane = new UserMutationExecutionLane();
        AgentMutationExecutionLane agentLane = new AgentMutationExecutionLane(sharedLane);
        UUID userId = UUID.randomUUID();
        CountDownLatch agentEntered = new CountDownLatch(1);
        CountDownLatch releaseAgent = new CountDownLatch(1);
        Future<?> agentMutation = executor.submit(() -> agentLane.execute(
                userId,
                AgentToolRisk.REVERSIBLE_MUTATION,
                Duration.ofSeconds(2),
                () -> {
                    agentEntered.countDown();
                    await(releaseAgent);
                    return null;
                }
        ));
        assertThat(agentEntered.await(1, TimeUnit.SECONDS)).isTrue();

        CountDownLatch cartEntered = new CountDownLatch(1);
        Future<?> cartMutation = executor.submit(() -> sharedLane.execute(userId, () -> {
            cartEntered.countDown();
            return null;
        }));

        assertThat(cartEntered.await(50, TimeUnit.MILLISECONDS)).isFalse();
        releaseAgent.countDown();
        agentMutation.get(1, TimeUnit.SECONDS);
        cartMutation.get(1, TimeUnit.SECONDS);
        assertThat(cartEntered.getCount()).isZero();
    }

    @Test
    void interruptedDirectMutationCannotRunLater() throws Exception {
        UserMutationExecutionLane sharedLane = new UserMutationExecutionLane();
        AgentMutationExecutionLane agentLane = new AgentMutationExecutionLane(sharedLane);
        UUID userId = UUID.randomUUID();
        CountDownLatch agentEntered = new CountDownLatch(1);
        CountDownLatch releaseAgent = new CountDownLatch(1);
        Future<?> agentMutation = executor.submit(() -> agentLane.execute(
                userId,
                AgentToolRisk.REVERSIBLE_MUTATION,
                Duration.ofSeconds(2),
                () -> {
                    agentEntered.countDown();
                    await(releaseAgent);
                    return null;
                }
        ));
        assertThat(agentEntered.await(1, TimeUnit.SECONDS)).isTrue();

        AtomicBoolean executed = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch directStarted = new CountDownLatch(1);
        Thread directMutation = Thread.ofVirtual().start(() -> {
            directStarted.countDown();
            try {
                sharedLane.execute(userId, () -> {
                    executed.set(true);
                    return null;
                });
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        assertThat(directStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(awaitParked(directMutation, Duration.ofSeconds(1))).isTrue();

        directMutation.interrupt();
        directMutation.join(1000);
        releaseAgent.countDown();
        agentMutation.get(1, TimeUnit.SECONDS);

        assertThat(directMutation.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOf(InterruptedException.class);
        assertThat(executed).isFalse();
    }

    private static boolean awaitParked(Thread thread, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.WAITING
                    || thread.getState() == Thread.State.TIMED_WAITING) {
                return true;
            }
            Thread.sleep(1);
        }
        return false;
    }

    private AgentMutationExecutionLane lane() {
        return new AgentMutationExecutionLane(new UserMutationExecutionLane());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("interrupted");
        }
    }
}
