package com.meant.api.module.user.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.entity.UserProductSearchQueryIntent;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.repository.UserProductSearchQueryIntentRepository;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class UserProductSearchQueryUnderstandingService {

    private static final String HIGH = "high";
    private static final String MEDIUM = "medium";
    private static final String LOW = "low";
    private static final String SYSTEM_PROMPT = """
            You convert natural shopping requests into concise merchant catalog search queries.
            Remove conversational wrapper text.
            Preserve concrete product nouns and hard search constraints such as price, size, material, color, brand, scent, and count.
            Keep subjective or contextual details as preferenceHints when they should inform ranking/explanations but are not good catalog keywords.
            Return a short lower-case searchQuery that a merchant product catalog can match.
            """;
    private static final List<Pattern> LEADING_WRAPPER_PATTERNS = List.of(
            Pattern.compile("^(please\\s+)?(can you|could you|would you)\\s+(please\\s+)?(show|find|get|look for|search for)\\s+(me\\s+)?"),
            Pattern.compile("^(please\\s+)?(show|find|get|search for|look for)\\s+(me\\s+)?"),
            Pattern.compile("^i\\s+(would like|want|need)\\s+(to\\s+)?(see|find|get|look at|look for|buy|shop for)\\s+"),
            Pattern.compile("^i'?m\\s+(looking|shopping|searching)\\s+for\\s+")
    );
    private static final Pattern LEADING_QUANTITY_PATTERN = Pattern.compile("^(some|a|an|the)\\s+");
    private static final Pattern FILLER_WORD_PATTERN = Pattern.compile("\\b(nice|good|great|cool|cute|pretty|best)\\b");
    private static final Pattern AMBIGUOUS_CONTEXT_PATTERN = Pattern.compile(
            "\\b(something|anything|recommend|suggest|gift|present|for my|for someone|similar to|like the one|what should)\\b"
    );
    private static final Pattern CATALOG_TOKEN_PATTERN = Pattern.compile("[a-z0-9]{3,}");

    private final OpenRouterChatClient openRouterChatClient;
    private final OpenRouterProperties openRouterProperties;
    private final UserProductSearchProperties userProductSearchProperties;
    private final UserProductSearchQueryIntentRepository userProductSearchQueryIntentRepository;
    private final UserProductSearchHashService userProductSearchHashService;
    private final ObjectMapper objectMapper;

    public UserProductSearchQueryIntentResult understand(String originalQuery) {
        String trimmedQuery = originalQuery.trim();
        String normalizedOriginalQuery = userProductSearchHashService.normalizeQuery(trimmedQuery);
        UserProductSearchQueryIntentResult deterministic = deterministicIntent(trimmedQuery, normalizedOriginalQuery);
        if (HIGH.equals(deterministic.confidence())) {
            return deterministic;
        }
        return cachedOrGeneratedIntent(trimmedQuery, normalizedOriginalQuery);
    }

    protected UserProductSearchQueryIntentResult cachedOrGeneratedIntent(
            String originalQuery,
            String normalizedOriginalQuery
    ) {
        String model = openRouterProperties.models().productSearchQueryParser();
        String promptVersion = userProductSearchProperties.queryParserPromptVersion();
        return userProductSearchQueryIntentRepository
                .findByNormalizedOriginalQueryAndModelAndPromptVersion(
                        normalizedOriginalQuery,
                        model,
                        promptVersion
                )
                .map(intent -> intent.toResult(originalQuery))
                .orElseGet(() -> saveGeneratedIntent(originalQuery, normalizedOriginalQuery, model, promptVersion));
    }

    private UserProductSearchQueryIntentResult saveGeneratedIntent(
            String originalQuery,
            String normalizedOriginalQuery,
            String model,
            String promptVersion
    ) {
        UserProductSearchQueryIntentResult generated = generateIntent(originalQuery, normalizedOriginalQuery, model);
        userProductSearchQueryIntentRepository.save(UserProductSearchQueryIntent.from(
                normalizedOriginalQuery,
                model,
                promptVersion,
                generated,
                Instant.now()
        ));
        return generated;
    }

    private UserProductSearchQueryIntentResult deterministicIntent(
            String originalQuery,
            String normalizedOriginalQuery
    ) {
        String stripped = stripDeterministicWrappers(normalizedOriginalQuery);
        String normalizedSearchQuery = userProductSearchHashService.normalizeQuery(stripped);
        String confidence = deterministicConfidence(normalizedOriginalQuery, normalizedSearchQuery);
        return new UserProductSearchQueryIntentResult(
                originalQuery,
                normalizedOriginalQuery,
                normalizedSearchQuery,
                normalizedSearchQuery,
                normalizedSearchQuery,
                List.of(),
                List.of(),
                confidence,
                "deterministic"
        );
    }

    private String stripDeterministicWrappers(String normalizedOriginalQuery) {
        String stripped = normalizedOriginalQuery;
        for (Pattern pattern : LEADING_WRAPPER_PATTERNS) {
            stripped = pattern.matcher(stripped).replaceFirst("");
        }
        stripped = LEADING_QUANTITY_PATTERN.matcher(stripped).replaceFirst("");
        stripped = FILLER_WORD_PATTERN.matcher(stripped).replaceAll("");
        stripped = userProductSearchHashService.normalizeQuery(stripped);
        stripped = LEADING_QUANTITY_PATTERN.matcher(stripped).replaceFirst("");
        return userProductSearchHashService.normalizeQuery(stripped);
    }

    private String deterministicConfidence(String normalizedOriginalQuery, String normalizedSearchQuery) {
        if (normalizedSearchQuery.isBlank() || !CATALOG_TOKEN_PATTERN.matcher(normalizedSearchQuery).find()) {
            return LOW;
        }
        if (AMBIGUOUS_CONTEXT_PATTERN.matcher(normalizedOriginalQuery).find()) {
            return LOW;
        }
        int tokenCount = normalizedSearchQuery.split("\\s+").length;
        return tokenCount <= 7 ? HIGH : MEDIUM; 
    }

    private UserProductSearchQueryIntentResult generateIntent(
            String originalQuery,
            String normalizedOriginalQuery,
            String model
    ) {
        String response = openRouterChatClient.completeJson(
                model,
                SYSTEM_PROMPT,
                "Shopping request:\n" + originalQuery,
                "product_search_query_intent",
                responseSchema()
        );
        return sanitizeResponse(originalQuery, normalizedOriginalQuery, response);
    }

    private OpenRouterJsonSchemaDefinition responseSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("searchQuery", "constraints", "preferenceHints", "confidence"),
                Map.of(
                        "searchQuery", OpenRouterJsonSchemaDefinition.string(),
                        "constraints", OpenRouterJsonSchemaDefinition.array(OpenRouterJsonSchemaDefinition.string()),
                        "preferenceHints", OpenRouterJsonSchemaDefinition.array(OpenRouterJsonSchemaDefinition.string()),
                        "confidence", OpenRouterJsonSchemaDefinition.stringEnum(List.of(HIGH, MEDIUM, LOW))
                )
        );
    }

    private UserProductSearchQueryIntentResult sanitizeResponse(
            String originalQuery,
            String normalizedOriginalQuery,
            String response
    ) {
        QueryIntentResponse parsed = parseResponse(response);
        String normalizedSearchQuery = userProductSearchHashService.normalizeQuery(parsed.searchQuery());
        if (normalizedSearchQuery.isBlank()) {
            normalizedSearchQuery = normalizedOriginalQuery;
        }
        List<String> constraints = sanitizeList(parsed.constraints());
        List<String> preferenceHints = sanitizeList(parsed.preferenceHints());
        String confidence = sanitizeConfidence(parsed.confidence());
        return new UserProductSearchQueryIntentResult(
                originalQuery,
                normalizedOriginalQuery,
                normalizedSearchQuery,
                normalizedSearchQuery,
                intentCacheKey(normalizedSearchQuery, constraints, preferenceHints),
                constraints,
                preferenceHints,
                confidence,
                "llm"
        );
    }

    private QueryIntentResponse parseResponse(String response) {
        try {
            QueryIntentResponse parsed = objectMapper.readValue(response, QueryIntentResponse.class);
            return parsed == null ? new QueryIntentResponse(null, List.of(), List.of(), LOW) : parsed;
        } catch (JacksonException exception) {
            throw new OpenRouterException("OpenRouter returned invalid product search query JSON", exception);
        }
    }

    private String intentCacheKey(
            String normalizedSearchQuery,
            List<String> constraints,
            List<String> preferenceHints
    ) {
        if (constraints.isEmpty() && preferenceHints.isEmpty()) {
            return normalizedSearchQuery;
        }
        return String.join("\n",
                "searchQuery=" + normalizedSearchQuery,
                "constraints=" + String.join("|", constraints),
                "preferenceHints=" + String.join("|", preferenceHints)
        );
    }

    private List<String> sanitizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> userProductSearchHashService.normalizeQuery(value).toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .limit(10)
                .forEach(sanitized::add);
        return List.copyOf(sanitized);
    }

    private String sanitizeConfidence(String value) {
        if (HIGH.equals(value) || MEDIUM.equals(value) || LOW.equals(value)) {
            return value;
        }
        return LOW;
    }

    private record QueryIntentResponse(
            String searchQuery,
            List<String> constraints,
            List<String> preferenceHints,
            String confidence
    ) {
    }
}
