package com.meant.api.module.user.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserProductSearchSuggestionsResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor
public class UserProductSearchSuggestionService {

    private static final int SUGGESTION_COUNT = 4;
    private static final String SYSTEM_PROMPT = """
            You write concise Meant product search suggestions.
            Generate exactly four clickable shopping prompts for the user.
            Base each prompt on the active shopping filters, budget, and location when provided.
            Make each prompt specific enough to run as a merchant catalog search.
            Do not mention internal filter IDs, database fields, or model behavior.
            Avoid duplicates and avoid generic assistant questions.
            """;

    private final UserSettingsService userSettingsService;
    private final OpenRouterChatClient openRouterChatClient;
    private final OpenRouterProperties openRouterProperties;
    private final ObjectMapper objectMapper;

    public UserProductSearchSuggestionsResult generate(@NotNull @Valid UpsertUserCommand command) {
        UserSettingsResult settings = userSettingsService.get(command);
        try {
            String response = openRouterChatClient.completeJson(
                    openRouterProperties.models().productSearchQueryParser(),
                    SYSTEM_PROMPT,
                    userPrompt(settings),
                    "product_search_suggestions",
                    responseSchema()
            );
            return new UserProductSearchSuggestionsResult(sanitize(response, settings));
        } catch (OpenRouterException exception) {
            return new UserProductSearchSuggestionsResult(sanitize(null, settings));
        }
    }

    private String userPrompt(UserSettingsResult settings) {
        return """
                Active shopping filters:
                %s

                Budget:
                %s

                Location:
                %s

                Return product-search prompts only.
                """.formatted(
                filterCatalog(settings.filters()),
                settings.budget() == null ? "Not set" : "$" + settings.budget(),
                location(settings.location()));
    }

    private String filterCatalog(List<ShoppingFilterResult> filters) {
        if (filters.isEmpty()) {
            return "No active filters.";
        }
        return filters.stream()
                .map(filter -> "- %s: %s".formatted(filter.label(), filter.description()))
                .collect(Collectors.joining("\n"));
    }

    private String location(UserLocationResult location) {
        if (location == null) {
            return "Not set";
        }
        return "%s, %s".formatted(location.city(), location.country());
    }

    private OpenRouterJsonSchemaDefinition responseSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("suggestions"),
                Map.of("suggestions", OpenRouterJsonSchemaDefinition.array(
                        OpenRouterJsonSchemaDefinition.string(),
                        SUGGESTION_COUNT,
                        SUGGESTION_COUNT
                ))
        );
    }

    private List<String> sanitize(String response, UserSettingsResult settings) {
        List<String> suggestions = sanitizeSuggestions(parseResponse(response).suggestions());
        if (suggestions.size() == SUGGESTION_COUNT) {
            return suggestions;
        }

        List<String> completed = new ArrayList<>(suggestions);
        for (String fallback : fallbackSuggestions(settings)) {
            addSuggestion(completed, fallback);
            if (completed.size() == SUGGESTION_COUNT) {
                break;
            }
        }
        return List.copyOf(completed);
    }

    private SuggestionsResponse parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return new SuggestionsResponse(List.of());
        }
        try {
            SuggestionsResponse parsed = objectMapper.readValue(response, SuggestionsResponse.class);
            return parsed == null ? new SuggestionsResponse(List.of()) : parsed;
        } catch (JacksonException exception) {
            throw new OpenRouterException("OpenRouter returned invalid product search suggestions JSON", exception);
        }
    }

    private List<String> sanitizeSuggestions(List<String> values) {
        if (values == null) {
            return List.of();
        }

        List<String> suggestions = new ArrayList<>();
        values.forEach(value -> addSuggestion(suggestions, value));
        return List.copyOf(suggestions);
    }

    private void addSuggestion(List<String> suggestions, String value) {
        if (suggestions.size() >= SUGGESTION_COUNT || value == null) {
            return;
        }

        String suggestion = value.trim().replaceAll("\\s+", " ");
        if (suggestion.isBlank()) {
            return;
        }

        Set<String> existing = suggestions.stream()
                .map(current -> current.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!existing.contains(suggestion.toLowerCase(Locale.ROOT))) {
            suggestions.add(suggestion);
        }
    }

    private List<String> fallbackSuggestions(UserSettingsResult settings) {
        Set<String> filterIds = settings.filters().stream()
                .map(ShoppingFilterResult::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String budgetSuffix = settings.budget() == null ? "" : " under $" + settings.budget();
        List<String> suggestions = new ArrayList<>();

        if (filterIds.contains("organic") || filterIds.contains("low-sugar") || filterIds.contains("gluten-free")) {
            suggestions.add("Find me a healthy breakfast cereal" + budgetSuffix);
        }
        if (filterIds.contains("natural-materials") || filterIds.contains("no-polyester")) {
            suggestions.add("A natural-material T-shirt" + budgetSuffix);
        }
        if (filterIds.contains("sustainable-brands")) {
            suggestions.add("Show me sustainable everyday essentials" + budgetSuffix);
        }
        if (filterIds.contains("fragrance-free") || filterIds.contains("paraben-free")) {
            suggestions.add("Find me gentle fragrance-free skincare" + budgetSuffix);
        }
        if (filterIds.contains("best-value") || filterIds.contains("highly-rated")) {
            suggestions.add("Best value highly rated products" + budgetSuffix);
        }
        suggestions.addAll(List.of(
                "Find me products that match my filters" + budgetSuffix,
                "Show me better options for my preferences" + budgetSuffix,
                "What should I buy next" + budgetSuffix,
                "Find me something healthy" + budgetSuffix
        ));
        return suggestions;
    }

    private record SuggestionsResponse(
            List<String> suggestions
    ) {
    }
}
