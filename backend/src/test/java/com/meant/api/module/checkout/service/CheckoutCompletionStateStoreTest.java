package com.meant.api.module.checkout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.checkout.entity.CheckoutCompletionState;
import com.meant.api.module.checkout.entity.CheckoutCompletionStatus;
import com.meant.api.module.checkout.exception.CheckoutSafetyException;
import com.meant.api.module.checkout.repository.CheckoutCompletionStateRepository;
import com.meant.api.module.checkout.service.command.AuthorizeCheckoutCompletionCommand;
import com.meant.api.module.checkout.service.command.StartCheckoutCompletionCommand;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class CheckoutCompletionStateStoreTest {

    private final FakeCheckoutCompletionStateRepository repository = new FakeCheckoutCompletionStateRepository();
    private final CheckoutCompletionStateStore store = new CheckoutCompletionStateStore(repository.proxy());

    @Test
    void compareAndSetAllowsOnlyOneConcurrentCompletionStart() throws Exception {
        store.authorize(new AuthorizeCheckoutCompletionCommand("co_123", UUID.randomUUID()));
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            attempts.add(() -> {
                ready.countDown();
                start.await();
                return store.tryStartCompletion(new StartCheckoutCompletionCommand("co_123"));
            });
        }

        try (var executor = Executors.newFixedThreadPool(8)) {
            List<java.util.concurrent.Future<Boolean>> futures = attempts.stream()
                    .map(executor::submit)
                    .toList();
            ready.await();
            start.countDown();

            assertThat(futures.stream().filter(this::completedTrue).count()).isEqualTo(1);
        }
        CheckoutCompletionState state = store.find(new StartCheckoutCompletionCommand("co_123"));
        assertThat(state.getStatus()).isEqualTo(CheckoutCompletionStatus.COMPLETION_IN_FLIGHT);
    }

    @Test
    void cancelIsBlockedAfterCompletionIsInFlight() {
        store.authorize(new AuthorizeCheckoutCompletionCommand("co_123", UUID.randomUUID()));

        assertThat(store.tryStartCompletion(new StartCheckoutCompletionCommand("co_123"))).isTrue();
        assertThat(store.tryCancel(new StartCheckoutCompletionCommand("co_123"))).isFalse();
    }

    @Test
    void completedStateCannotBeReauthorized() {
        store.authorize(new AuthorizeCheckoutCompletionCommand("co_123", UUID.randomUUID()));
        store.tryStartCompletion(new StartCheckoutCompletionCommand("co_123"));
        store.markCompleted(new StartCheckoutCompletionCommand("co_123"));

        assertThatThrownBy(() -> store.authorize(new AuthorizeCheckoutCompletionCommand("co_123", UUID.randomUUID())))
                .isInstanceOf(CheckoutSafetyException.class)
                .hasMessageContaining("cannot be re-authorized");
    }

    private boolean completedTrue(java.util.concurrent.Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class FakeCheckoutCompletionStateRepository {

        private final Map<String, CheckoutCompletionState> records = new java.util.concurrent.ConcurrentHashMap<>();

        private CheckoutCompletionStateRepository proxy() {
            return (CheckoutCompletionStateRepository) Proxy.newProxyInstance(
                    CheckoutCompletionStateRepository.class.getClassLoader(),
                    new Class<?>[]{CheckoutCompletionStateRepository.class},
                    (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("findByCheckoutIdHash".equals(methodName)) {
                            return Optional.ofNullable(records.get(args[0]));
                        }
                        if ("findReadOnlyByCheckoutIdHash".equals(methodName)) {
                            return Optional.ofNullable(records.get(args[0]));
                        }
                        if ("save".equals(methodName)) {
                            CheckoutCompletionState state = (CheckoutCompletionState) args[0];
                            records.put(state.getCheckoutIdHash(), state);
                            return state;
                        }
                        if ("compareAndSetStatus".equals(methodName)) {
                            return compareAndSet(
                                    (String) args[0],
                                    (CheckoutCompletionStatus) args[1],
                                    (CheckoutCompletionStatus) args[2],
                                    (Instant) args[3]
                            );
                        }
                        if ("toString".equals(methodName)) {
                            return "FakeCheckoutCompletionStateRepository";
                        }
                        throw new UnsupportedOperationException(methodName);
                    }
            );
        }

        private synchronized int compareAndSet(
                String checkoutIdHash,
                CheckoutCompletionStatus expected,
                CheckoutCompletionStatus replacement,
                Instant updatedAt
        ) {
            CheckoutCompletionState state = records.get(checkoutIdHash);
            if (state == null || state.getStatus() != expected) {
                return 0;
            }
            state.transitionTo(replacement, updatedAt);
            return 1;
        }
    }
}
