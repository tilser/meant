package com.meant.api.module.user.controller;

import com.meant.api.module.user.controller.response.UserAssistantStreamEventResponse;
import com.meant.api.module.user.controller.response.UserProductSearchStreamEventResponse;
import com.meant.api.module.user.controller.response.UserFederatedProductSearchStreamEventResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class UserStreamEventWriter {

    private final ObjectMapper objectMapper;

    void writeAssistantEvent(
            OutputStream outputStream,
            UserAssistantStreamEventResponse event
    ) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outputStream.write(("event: " + event.type() + "\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write(("data: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    void writeProductSearchEvent(
            SseEmitter emitter,
            UserProductSearchStreamEventResponse event
    ) throws IOException {
        String payload = objectMapper.writeValueAsString(event);
        emitter.send(SseEmitter.event()
                .name(event.type())
                .data(payload));
    }

    void writeFederatedProductSearchEvent(
            SseEmitter emitter,
            UserFederatedProductSearchStreamEventResponse event
    ) throws IOException {
        String payload = objectMapper.writeValueAsString(event);
        emitter.send(SseEmitter.event()
                .name(event.type().name().toLowerCase(java.util.Locale.ROOT))
                .data(payload));
    }
}
