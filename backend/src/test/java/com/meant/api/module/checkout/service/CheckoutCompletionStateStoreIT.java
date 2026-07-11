package com.meant.api.module.checkout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.checkout.entity.CheckoutCompletionStatus;
import com.meant.api.module.checkout.service.command.AuthorizeCheckoutCompletionCommand;
import com.meant.api.module.checkout.service.command.StartCheckoutCompletionCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CheckoutCompletionStateStoreIT extends PostgresIntegrationTestSupport {

    @Autowired
    private CheckoutCompletionStateStore store;

    @Test
    void readOnlyFindUsesNonLockingQueryOnPostgres() {
        String checkoutId = uniqueCheckoutId();
        store.authorize(new AuthorizeCheckoutCompletionCommand(checkoutId, null));

        assertThatCode(() -> store.find(new StartCheckoutCompletionCommand(checkoutId)))
                .doesNotThrowAnyException();
        assertThat(store.find(new StartCheckoutCompletionCommand(checkoutId)).getStatus())
                .isEqualTo(CheckoutCompletionStatus.AUTHORIZED_TO_COMPLETE);
    }

    @Test
    void concurrentCompleteAttemptsAllowExactlyOneProceedOnPostgres() throws Exception {
        String checkoutId = uniqueCheckoutId();
        store.authorize(new AuthorizeCheckoutCompletionCommand(checkoutId, null));
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            attempts.add(() -> {
                ready.countDown();
                start.await();
                return store.tryStartCompletion(new StartCheckoutCompletionCommand(checkoutId));
            });
        }

        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> futures = attempts.stream()
                    .map(executor::submit)
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(futures.stream().filter(this::completedTrue).count()).isEqualTo(1);
        }
        assertThat(store.find(new StartCheckoutCompletionCommand(checkoutId)).getStatus())
                .isEqualTo(CheckoutCompletionStatus.COMPLETION_IN_FLIGHT);
    }

    @Test
    void cancelAndCompleteRaceAllowsOnlyOneStateTransitionOnPostgres() throws Exception {
        String checkoutId = uniqueCheckoutId();
        store.authorize(new AuthorizeCheckoutCompletionCommand(checkoutId, null));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<CheckoutCompletionStatus>> attempts = List.of(
                () -> {
                    ready.countDown();
                    start.await();
                    return store.tryStartCompletion(new StartCheckoutCompletionCommand(checkoutId))
                            ? CheckoutCompletionStatus.COMPLETION_IN_FLIGHT
                            : null;
                },
                () -> {
                    ready.countDown();
                    start.await();
                    return store.tryCancel(new StartCheckoutCompletionCommand(checkoutId))
                            ? CheckoutCompletionStatus.CANCELED
                            : null;
                }
        );

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<CheckoutCompletionStatus>> futures = attempts.stream()
                    .map(executor::submit)
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<CheckoutCompletionStatus> winners = futures.stream()
                    .map(this::completedStatus)
                    .filter(Objects::nonNull)
                    .toList();
            assertThat(winners).hasSize(1);
            assertThat(store.find(new StartCheckoutCompletionCommand(checkoutId)).getStatus())
                    .isEqualTo(winners.get(0))
                    .isIn(CheckoutCompletionStatus.COMPLETION_IN_FLIGHT, CheckoutCompletionStatus.CANCELED);
        }
    }

    @Test
    void remoteCompletedReconciliationRecoversAuthorizedAndStuckInFlightStatesOnPostgres() {
        String authorizedCheckoutId = uniqueCheckoutId();
        StartCheckoutCompletionCommand authorizedCommand = new StartCheckoutCompletionCommand(authorizedCheckoutId);
        store.authorize(new AuthorizeCheckoutCompletionCommand(authorizedCheckoutId, null));

        store.markCompletedFromRemoteStatus(authorizedCommand);

        assertThat(store.find(authorizedCommand).getStatus()).isEqualTo(CheckoutCompletionStatus.COMPLETED);
        assertThatCode(() -> store.markCompletedFromRemoteStatus(authorizedCommand)).doesNotThrowAnyException();

        String inFlightCheckoutId = uniqueCheckoutId();
        StartCheckoutCompletionCommand inFlightCommand = new StartCheckoutCompletionCommand(inFlightCheckoutId);
        store.authorize(new AuthorizeCheckoutCompletionCommand(inFlightCheckoutId, null));
        assertThat(store.tryStartCompletion(inFlightCommand)).isTrue();

        store.markCompletedFromRemoteStatus(inFlightCommand);

        assertThat(store.find(inFlightCommand).getStatus()).isEqualTo(CheckoutCompletionStatus.COMPLETED);
    }

    private String uniqueCheckoutId() {
        return "co_" + UUID.randomUUID();
    }

    private boolean completedTrue(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private CheckoutCompletionStatus completedStatus(Future<CheckoutCompletionStatus> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
