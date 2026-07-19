package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AgentRunEventNotifierTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publishesOnlyAfterTheSurroundingTransactionCommits() throws InterruptedException {
        AgentRunEventNotifier notifier = new AgentRunEventNotifier();
        UUID runId = UUID.randomUUID();
        try (var subscription = notifier.subscribe(runId, 2)) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();

            notifier.signalAfterCommit(runId, 7L);

            assertThat(subscription.awaitLatestCursor(Duration.ZERO)).isEmpty();
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            assertThat(subscription.awaitLatestCursor(Duration.ofMillis(10)))
                    .hasValue(7L);
        }
    }

    @Test
    void boundedSubscriberCoalescesOverflowToTheLatestDurableCursor() throws InterruptedException {
        AgentRunEventNotifier notifier = new AgentRunEventNotifier();
        UUID runId = UUID.randomUUID();
        try (var subscription = notifier.subscribe(runId, 1)) {
            notifier.signalAfterCommit(runId, 1L);
            notifier.signalAfterCommit(runId, 2L);
            notifier.signalAfterCommit(runId, 3L);

            assertThat(subscription.awaitLatestCursor(Duration.ofMillis(10)))
                    .hasValue(3L);
        }
    }

    @Test
    void rolledBackTransactionDoesNotPublishAPhantomWakeup() throws InterruptedException {
        AgentRunEventNotifier notifier = new AgentRunEventNotifier();
        UUID runId = UUID.randomUUID();
        try (var subscription = notifier.subscribe(runId, 1)) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();

            notifier.signalAfterCommit(runId, 9L);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK));

            assertThat(subscription.awaitLatestCursor(Duration.ZERO)).isEmpty();
        }
    }

    @Test
    void closingSubscriptionIsIdempotentAndRemovesItFromTheRun() throws InterruptedException {
        AgentRunEventNotifier notifier = new AgentRunEventNotifier();
        UUID runId = UUID.randomUUID();
        var subscription = notifier.subscribe(runId, 1);

        subscription.close();
        subscription.close();
        notifier.signalAfterCommit(runId, 4L);

        assertThat(notifier.subscriberCount(runId)).isZero();
        assertThat(subscription.awaitLatestCursor(Duration.ZERO)).isEmpty();
    }
}
