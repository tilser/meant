package com.meant.api.module.user.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.OpenRouterJsonExtractor;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.service.dto.UserAssistantPageContext;
import com.meant.api.module.user.service.dto.UserAssistantRoute;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserAssistantRouteClassifier {

    private final OpenRouterChatClient openRouterChatClient;
    private final OpenRouterProperties openRouterProperties;
    private final ObjectMapper objectMapper;
    private final UserAssistantPromptContextBuilder promptContextBuilder;

    public UserAssistantRoute route(
            String userMessage,
            UserSettingsResult settings,
            UserAssistantPageContext pageContext
    ) {
        try {
            String response = openRouterChatClient.completeJson(
                    openRouterProperties.models().chatModel(),
                    routeSystemPrompt(),
                    promptContextBuilder.routeUserPrompt(userMessage, settings, pageContext),
                    "assistant_route",
                    routeSchema()
            );
            AssistantRouteResponse route = parseRouteResponse(response);
            if (route == null) {
                return UserAssistantRoute.fallback(userMessage);
            }
            return UserAssistantRoute.from(
                    route.action(),
                    route.searchQuery(),
                    route.clarifyingQuestion(),
                    userMessage
            );
        } catch (OpenRouterException | JacksonException exception) {
            log.info("Failed to classify assistant message; using local fallback ({})",
                    exception.getClass().getSimpleName());
            return UserAssistantRoute.fallback(userMessage);
        }
    }

    private AssistantRouteResponse parseRouteResponse(String response) throws JacksonException {
        try {
            return objectMapper.readValue(
                    OpenRouterJsonExtractor.objectCandidate(response),
                    AssistantRouteResponse.class);
        } catch (JacksonException exception) {
            Map<String, String> values = OpenRouterJsonExtractor.looseKeyValues(
                    response,
                    List.of("action", "searchQuery", "clarifyingQuestion")
            );
            if (!values.isEmpty()) {
                return new AssistantRouteResponse(
                        values.get("action"),
                        values.get("searchQuery"),
                        values.get("clarifyingQuestion")
                );
            }
            throw exception;
        }
    }

    private String routeSystemPrompt() {
        return """
                Classify a Meant floating assistant message.
                Return search_products when the user wants to find, browse, compare alternatives, or get recommendations for products.
                Return clarify only when a shopping request is too vague to search because it lacks the product type, recipient, occasion, or usable constraint.
                Return answer for account, order, cart, saved-item, preference, navigation, explanation, or general follow-up questions.
                If returning search_products, write a concise merchant-search query in searchQuery.
                If returning clarify, write one short clarifyingQuestion.
                """;
    }

    private OpenRouterJsonSchemaDefinition routeSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("action", "searchQuery", "clarifyingQuestion"),
                Map.of(
                        "action", OpenRouterJsonSchemaDefinition.stringEnum(List.of(
                                UserAssistantRoute.ACTION_ANSWER,
                                UserAssistantRoute.ACTION_SEARCH,
                                UserAssistantRoute.ACTION_CLARIFY
                        )),
                        "searchQuery", OpenRouterJsonSchemaDefinition.string(),
                        "clarifyingQuestion", OpenRouterJsonSchemaDefinition.string()
                )
        );
    }

    private record AssistantRouteResponse(
            String action,
            String searchQuery,
            String clarifyingQuestion
    ) {
    }
}
