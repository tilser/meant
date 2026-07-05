package com.meant.api.module.user.service;

import com.meant.api.module.user.service.dto.UserAssistantStreamEvent;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class UserAssistantStreamEventAdapter {

    public void emitText(String text, Consumer<UserAssistantStreamEvent> eventConsumer) {
        for (String chunk : text.split("(?<=\\s)")) {
            if (!chunk.isBlank()) {
                eventConsumer.accept(UserAssistantStreamEvent.delta(chunk));
            }
        }
    }
}
