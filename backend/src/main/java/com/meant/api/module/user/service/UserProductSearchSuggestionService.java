package com.meant.api.module.user.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
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
import java.util.regex.Pattern;
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
    private static final int MAX_SUGGESTION_LENGTH = 64;
    private static final Pattern PRICE_OR_BUDGET_PATTERN = Pattern.compile(
            "(?i)([$€£¥]|\\b(usd|u\\.s\\. dollars?|us dollars?|dollars?|eur|euros?|gbp|pounds?|czk|crowns?)\\b"
                    + "|\\d+(?:\\.\\d+)?\\s*(usd|u\\.s\\. dollars?|us dollars?|dollars?|eur|euros?|gbp|pounds?|czk|crowns?)\\b"
                    + "|\\b(under|over|below|above|less than|more than|up to|around|about|approximately|max(?:imum)?|budget)\\s*[$€£¥]?\\s*\\d"
                    + "|\\b(cheap|affordable|budget-friendly|low-cost)\\b)"
    );
    private static final Pattern PLACE_QUALIFIER_PATTERN = Pattern.compile(
            "\\b(?:in|near|around)\\s+[A-Z][A-Za-z]+(?:\\s+[A-Z][A-Za-z]+){0,3}\\b"
    );
    private static final Pattern LOCAL_PLACE_PATTERN = Pattern.compile(
            "(?i)\\b(near me|nearby|local|in my area|around me)\\b"
    );
    private static final String SYSTEM_PROMPT = """
            You write concise Meant product search suggestions.
            Generate exactly four clickable shopping prompts for the user.
            Base each prompt on the active shopping filters when provided.
            Make each prompt specific enough to run as a merchant catalog search.
            Keep each prompt short and product-focused.
            Do not include prices, budgets, currency, cities, countries, stores, or places.
            Do not mention internal filter IDs, database fields, or model behavior.
            Avoid duplicates and avoid generic assistant questions.
            """;

    private final UserSettingsService userSettingsService;
    private final OpenRouterChatClient openRouterChatClient;
    private final OpenRouterProperties openRouterProperties;
    private final ObjectMapper objectMapper;

    public UserProductSearchSuggestionsResult generate(@NotNull @Valid EnsureUserProfileCommand command) {
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

                Return short product-search prompts only. Do not mention prices or places.
                """.formatted(
                filterCatalog(settings.filters()));
    }

    private String filterCatalog(List<ShoppingFilterResult> filters) {
        if (filters.isEmpty()) {
            return "No active filters.";
        }
        return filters.stream()
                .map(filter -> "- %s: %s".formatted(filter.label(), filter.description()))
                .collect(Collectors.joining("\n"));
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
        List<String> suggestions = sanitizeSuggestions(parseResponse(response).suggestions(), settings);
        if (suggestions.size() == SUGGESTION_COUNT) {
            return suggestions;
        }

        List<String> completed = new ArrayList<>(suggestions);
        for (String fallback : fallbackSuggestions(settings)) {
            addSuggestion(completed, fallback, settings);
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

    private List<String> sanitizeSuggestions(List<String> values, UserSettingsResult settings) {
        if (values == null) {
            return List.of();
        }

        List<String> suggestions = new ArrayList<>();
        values.forEach(value -> addSuggestion(suggestions, value, settings));
        return List.copyOf(suggestions);
    }

    private void addSuggestion(List<String> suggestions, String value, UserSettingsResult settings) {
        if (suggestions.size() >= SUGGESTION_COUNT || value == null) {
            return;
        }

        String suggestion = value.trim().replaceAll("\\s+", " ");
        if (suggestion.isBlank()) {
            return;
        }
        if (hasDisallowedSuggestionContent(suggestion, settings)) {
            return;
        }

        Set<String> existing = suggestions.stream()
                .map(current -> current.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!existing.contains(suggestion.toLowerCase(Locale.ROOT))) {
            suggestions.add(suggestion);
        }
    }

    private boolean hasDisallowedSuggestionContent(String suggestion, UserSettingsResult settings) {
        if (suggestion.length() > MAX_SUGGESTION_LENGTH) {
            return true;
        }
        if (PRICE_OR_BUDGET_PATTERN.matcher(suggestion).find()) {
            return true;
        }
        if (PLACE_QUALIFIER_PATTERN.matcher(suggestion).find() || LOCAL_PLACE_PATTERN.matcher(suggestion).find()) {
            return true;
        }
        return settings.locations().stream().anyMatch(location -> containsLocation(suggestion, location));
    }

    private boolean containsLocation(String suggestion, UserLocationResult location) {
        return containsWord(suggestion, location.city()) || containsWord(suggestion, location.country());
    }

    private boolean containsWord(String value, String word) {
        if (word == null || word.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile("(?i)(^|\\W)" + Pattern.quote(word.trim()) + "($|\\W)");
        return pattern.matcher(value).find();
    }

    private List<String> fallbackSuggestions(UserSettingsResult settings) {
        Set<String> filterIds = settings.filters().stream()
                .map(ShoppingFilterResult::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> suggestions = new ArrayList<>();

        if (filterIds.contains("organic") || filterIds.contains("low-sugar") || filterIds.contains("gluten-free")) {
            suggestions.add("Find me a healthy breakfast cereal");
        }
        if (filterIds.contains("natural-materials") || filterIds.contains("no-polyester")) {
            suggestions.add("Find me a natural-material T-shirt");
        }
        if (filterIds.contains("sustainable-brands")) {
            suggestions.add("Show me sustainable everyday essentials");
        }
        if (filterIds.contains("fragrance-free") || filterIds.contains("paraben-free")) {
            suggestions.add("Find me gentle fragrance-free skincare");
        }
        if (filterIds.contains("best-value") || filterIds.contains("highly-rated")) {
            suggestions.add("Find me highly rated everyday products");
        }
        suggestions.addAll(List.of(
                "Find me products that match my filters",
                "Show me better options for my preferences",
                "What should I buy next",
                "Find me something healthy"
        ));
        return suggestions;
    }

    private record SuggestionsResponse(
            List<String> suggestions
    ) {
    }
}
