package com.meant.api.module.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.controller.response.UserProductSearchStreamEventResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class UserProductSearchSseSessionTest {

    @Test
    void admitsExactlyOneTerminalEventAndSuppressesLateEvents() throws Exception {
        CapturingProductSearchEventWriter writer = new CapturingProductSearchEventWriter();
        UserSseSession<UserProductSearchStreamEventResponse> session = new UserSseSession<>(
                new SseEmitter(10_000L),
                4,
                UUID.randomUUID(),
                null,
                writer::writeProductSearchEvent,
                event -> "done".equals(event.type()) || "error".equals(event.type()),
                ignored -> UserProductSearchStreamEventResponse.error("search failed")
        );
        CountDownLatch releaseSearch = new CountDownLatch(1);
        try {
            session.start(() -> {
                try {
                    releaseSearch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });

            session.send(UserProductSearchStreamEventResponse.error("first terminal"));
            session.send(UserProductSearchStreamEventResponse.error("second terminal"));
            session.send(UserProductSearchStreamEventResponse.from(
                    com.meant.api.module.user.service.dto.UserProductSearchStreamEvent.phase("search", "late")
            ));

            assertThat(writer.written.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(writer.events).singleElement().satisfies(event -> {
                assertThat(event.type()).isEqualTo("error");
                assertThat(event.message()).isEqualTo("first terminal");
            });
        } finally {
            releaseSearch.countDown();
        }
    }

    @Test
    void completeWithErrorCancelsRunningSearchFuture() throws Exception {
        FailingProductSearchEventWriter writer = new FailingProductSearchEventWriter();
        UserSseSession<UserProductSearchStreamEventResponse> session = new UserSseSession<>(
                new SseEmitter(10_000L),
                4,
                UUID.randomUUID(),
                null,
                writer::writeProductSearchEvent,
                event -> "done".equals(event.type()) || "error".equals(event.type()),
                ignored -> UserProductSearchStreamEventResponse.error("search failed")
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
            UserSseSession<?> session,
            String fieldName
    ) throws ReflectiveOperationException {
        Field field = UserSseSession.class.getDeclaredField(fieldName);
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

    private static final class CapturingProductSearchEventWriter extends UserStreamEventWriter {

        private final CopyOnWriteArrayList<UserProductSearchStreamEventResponse> events = new CopyOnWriteArrayList<>();
        private final CountDownLatch written = new CountDownLatch(1);

        private CapturingProductSearchEventWriter() {
            super(null);
        }

        @Override
        void writeProductSearchEvent(SseEmitter emitter, UserProductSearchStreamEventResponse event) {
            events.add(event);
            written.countDown();
        }
    }
}
