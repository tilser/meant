package com.meant.api.module.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.controller.response.UserProductSearchStreamEventResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class UserProductSearchSseSessionTest {

    @Test
    void completeWithErrorCancelsRunningSearchFuture() throws Exception {
        FailingProductSearchEventWriter writer = new FailingProductSearchEventWriter();
        UserProductSearchSseSession session = new UserProductSearchSseSession(
                new SseEmitter(10_000L),
                4,
                UUID.randomUUID(),
                null,
                writer
        );
        CountDownLatch searchStarted = new CountDownLatch(1);
        CountDownLatch searchInterrupted = new CountDownLatch(1);
        CountDownLatch releaseSearch = new CountDownLatch(1);

        try {
            session.start(() -> {
                searchStarted.countDown();
                try {
                    releaseSearch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    searchInterrupted.countDown();
                }
            });

            assertThat(searchStarted.await(1, TimeUnit.SECONDS)).isTrue();

            session.send(UserProductSearchStreamEventResponse.error("client disconnected"));

            assertThat(writer.writeAttempted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(eventuallyCancelled(future(session, "searchFuture"))).isTrue();
            assertThat(searchInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseSearch.countDown();
        }
    }

    private static CompletableFuture<Void> future(
            UserProductSearchSseSession session,
            String fieldName
    ) throws ReflectiveOperationException {
        Field field = UserProductSearchSseSession.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        CompletableFuture<Void> future = (CompletableFuture<Void>) field.get(session);
        return future;
    }

    private static boolean eventuallyCancelled(CompletableFuture<?> future) throws InterruptedException {
        for (int attempt = 0; attempt < 10; attempt++) {
            if (future.isCancelled()) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(50L);
        }
        return false;
    }

    private static final class FailingProductSearchEventWriter extends UserStreamEventWriter {

        private final CountDownLatch writeAttempted = new CountDownLatch(1);

        private FailingProductSearchEventWriter() {
            super(null);
        }

        @Override
        void writeProductSearchEvent(
                SseEmitter emitter,
                UserProductSearchStreamEventResponse event
        ) throws IOException {
            writeAttempted.countDown();
            throw new IOException("client disconnected");
        }
    }
}
