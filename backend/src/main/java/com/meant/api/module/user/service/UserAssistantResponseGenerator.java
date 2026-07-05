package com.meant.api.module.user.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.module.user.entity.UserAssistantMessage;
import com.meant.api.module.user.service.dto.UserAssistantPageContext;
import com.meant.api.module.user.service.dto.UserAssistantRoute;
import com.meant.api.module.user.service.dto.UserAssistantStreamEvent;
import com.meant.api.module.user.service.dto.UserAssistantToolContext;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserAssistantResponseGenerator {

    private final OpenRouterChatClient openRouterChatClient;
    private final OpenRouterProperties openRouterProperties;
    private final UserAssistantPromptContextBuilder promptContextBuilder;
    private final UserAssistantFallbackRenderer fallbackRenderer;
    private final UserAssistantStreamEventAdapter streamEventAdapter;

    public String answer(
            UserAssistantRoute route,
            String userMessage,
            UserSettingsResult settings,
            UserAssistantPageContext pageContext,
            UserAssistantToolContext toolContext,
            List<UserAssistantMessage> history,
            List<UserProductSearchProductResult> products,
            String searchError,
            Consumer<UserAssistantStreamEvent> eventConsumer
    ) {
        if (route.isClarify()) {
            String text = route.clarifyingQuestion();
            streamEventAdapter.emitText(text, eventConsumer);
            return text;
        }

        if (route.isSearch() && products.isEmpty()) {
            String text = searchError == null
                    ? "I searched for that, but I do not have matching products to recommend yet. Try broadening the request or changing the merchant scope."
                    : "I could not run that product search right now. Try again in a moment.";
            streamEventAdapter.emitText(text, eventConsumer);
            return text;
        }

        StringBuilder streamed = new StringBuilder();
        try {
            openRouterChatClient.streamText(
                    openRouterProperties.models().chatModel(),
                    promptContextBuilder.chatMessages(route, userMessage, settings, pageContext, toolContext, history, products),
                    chunk -> {
                        streamed.append(chunk);
                        eventConsumer.accept(UserAssistantStreamEvent.delta(chunk));
                    }
            );
        } catch (OpenRouterException exception) {
            String partialAnswer = streamed.toString().trim();
            if (!partialAnswer.isBlank()) {
                log.warn("Failed to stream assistant answer after emitting partial text; preserving partial answer", exception);
                return partialAnswer;
            }
            log.warn("Failed to stream assistant answer before emitting text; using local fallback", exception);
            String fallback = fallbackRenderer.fallbackAnswer(route, settings, pageContext, toolContext, products);
            streamEventAdapter.emitText(fallback, eventConsumer);
            return fallback;
        }

        String answer = streamed.toString().trim();
        if (answer.isBlank()) {
            String fallback = fallbackRenderer.fallbackAnswer(route, settings, pageContext, toolContext, products);
            streamEventAdapter.emitText(fallback, eventConsumer);
            return fallback;
        }
        return answer;
    }
}
