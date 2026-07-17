package com.meant.api.module.user.controller;

import com.meant.api.module.user.controller.response.UserFederatedProductSearchStreamEventResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class UserStreamEventWriter {

    private final ObjectMapper objectMapper;

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
