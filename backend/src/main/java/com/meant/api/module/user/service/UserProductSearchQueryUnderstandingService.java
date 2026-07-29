package com.meant.api.module.user.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.OpenRouterJsonExtractor;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.entity.UserProductSearchQueryIntent;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.repository.UserProductSearchQueryIntentRepository;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
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
            Return displayQuery as a concise human-facing search phrase in normal title/sentence casing.
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
    private static final List<Pattern> SIMILARITY_WRAPPER_PATTERNS = List.of(
            Pattern.compile("^(?:products?\\s+)?similar\\s+to\\s+"),
            Pattern.compile("^similar\\b(?:\\s+products?)?(?:\\s+to)?\\s*")
    );
    private static final Pattern CATALOG_TOKEN_PATTERN = Pattern.compile("[a-z0-9]{3,}");
    private static final java.util.Set<String> UNPROTECTED_CONNECTORS = java.util.Set.of(
            "a", "an", "the", "and", "or", "for", "to", "of", "in", "on", "at", "with", "my",
            "birthday", "summer", "travel", "teacher", "gift", "present", "mom", "mum", "dad", "aunt",
            "uncle", "friend", "someone", "something");
    private static final List<String> RESPONSE_KEYS = List.of(
            "searchQuery",
            "displayQuery",
            "constraints",
            "preferenceHints",
            "confidence"
    );

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

    /**
     * Preserves the already qualified query without a second model rewrite.
     *
     * <p>The qualification model has already validated every content term against trusted buyer,
     * profile, preference, or taste evidence. Running the legacy query-understanding model again
     * could add an ungrounded keyword after that validation boundary.</p>
     */
    public UserProductSearchQueryIntentResult understandQualified(String qualifiedQuery) {
        String trimmedQuery = qualifiedQuery.trim();
        String normalizedQuery = userProductSearchHashService.normalizeQuery(trimmedQuery);
        return deterministicIntent(
                trimmedQuery,
                normalizedQuery,
                normalizedQuery,
                "qualified-authoritative"
        );
    }

    public UserProductSearchQueryIntentResult understandSimilarity(String originalQuery) {
        String trimmedQuery = originalQuery.trim();
        String normalizedOriginalQuery = userProductSearchHashService.normalizeQuery(trimmedQuery);
        String narrowedQuery = stripSimilarityWrappers(stripDeterministicWrappers(normalizedOriginalQuery));
        return deterministicIntent(
                trimmedQuery,
                normalizedOriginalQuery,
                narrowedQuery.isBlank() ? "products" : narrowedQuery,
                "similarity-deterministic"
        );
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
                .map(intent -> authoritativeOrDeterministic(
                        intent.toResult(originalQuery), originalQuery, normalizedOriginalQuery))
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
        return deterministicIntent(originalQuery, normalizedOriginalQuery, "deterministic");
    }

    private UserProductSearchQueryIntentResult deterministicIntent(
            String originalQuery,
            String normalizedOriginalQuery,
            String source
    ) {
        return deterministicIntent(
                originalQuery,
                normalizedOriginalQuery,
                stripDeterministicWrappers(normalizedOriginalQuery),
                source
        );
    }

    private UserProductSearchQueryIntentResult deterministicIntent(
            String originalQuery,
            String normalizedOriginalQuery,
            String searchQuery,
            String source
    ) {
        String normalizedSearchQuery = userProductSearchHashService.normalizeQuery(searchQuery);
        String confidence = deterministicConfidence(normalizedOriginalQuery, normalizedSearchQuery);
        return new UserProductSearchQueryIntentResult(
                originalQuery,
                normalizedOriginalQuery,
                normalizedSearchQuery,
                normalizedSearchQuery,
                displayQuery(normalizedSearchQuery),
                normalizedSearchQuery,
                normalizedSearchQuery,
                List.of(),
                List.of(),
                confidence,
                source
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

    private String stripSimilarityWrappers(String normalizedQuery) {
        String stripped = normalizedQuery;
        for (Pattern pattern : SIMILARITY_WRAPPER_PATTERNS) {
            stripped = pattern.matcher(stripped).replaceFirst("");
        }
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
        String response;
        try {
            response = openRouterChatClient.completeJson(
                    model,
                    SYSTEM_PROMPT,
                    "Shopping request:\n" + originalQuery,
                    "product_search_query_intent",
                    responseSchema()
            );
        } catch (OpenRouterException exception) {
            log.warn("Could not generate product search query intent; using deterministic fallback ({})",
                    exception.getClass().getSimpleName());
            return deterministicIntent(originalQuery, normalizedOriginalQuery, "llm-fallback");
        }
        try {
            return sanitizeResponse(originalQuery, normalizedOriginalQuery, response);
        } catch (RuntimeException exception) {
            log.warn("Could not parse product search query intent; using deterministic fallback ({})",
                    exception.getClass().getSimpleName());
            return deterministicIntent(originalQuery, normalizedOriginalQuery, "llm-fallback");
        }
    }

    private OpenRouterJsonSchemaDefinition responseSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("searchQuery", "displayQuery", "constraints", "preferenceHints", "confidence"),
                Map.of(
                        "searchQuery", OpenRouterJsonSchemaDefinition.string(),
                        "displayQuery", OpenRouterJsonSchemaDefinition.string(),
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
        String authoritativeBase = stripDeterministicWrappers(normalizedOriginalQuery);
        if (!preservesAuthoritativeTerms(normalizedSearchQuery, authoritativeBase)) {
            throw new OpenRouterException("Generated search query dropped authoritative shopping terms");
        }
        String displayQuery = sanitizeDisplayQuery(parsed.displayQuery(), normalizedSearchQuery);
        String normalizedDisplayQuery = userProductSearchHashService.normalizeQuery(displayQuery);
        List<String> constraints = sanitizeList(parsed.constraints());
        List<String> preferenceHints = sanitizeList(parsed.preferenceHints());
        String confidence = sanitizeConfidence(parsed.confidence());
        return new UserProductSearchQueryIntentResult(
                originalQuery,
                normalizedOriginalQuery,
                normalizedSearchQuery,
                normalizedSearchQuery,
                displayQuery,
                normalizedDisplayQuery,
                intentCacheKey(normalizedSearchQuery, constraints, preferenceHints),
                constraints,
                preferenceHints,
                confidence,
                "llm"
        );
    }

    private UserProductSearchQueryIntentResult authoritativeOrDeterministic(
            UserProductSearchQueryIntentResult candidate,
            String originalQuery,
            String normalizedOriginalQuery
    ) {
        String authoritativeBase = stripDeterministicWrappers(normalizedOriginalQuery);
        if (preservesAuthoritativeTerms(candidate.searchQuery(), authoritativeBase)) {
            return candidate;
        }
        log.warn("Cached product search query intent dropped authoritative terms; using deterministic fallback");
        return deterministicIntent(originalQuery, normalizedOriginalQuery, "cache-validation-fallback");
    }

    private boolean preservesAuthoritativeTerms(String candidate, String authoritativeBase) {
        java.util.Set<String> candidateTokens = Arrays.stream(
                        userProductSearchHashService.normalizeQuery(candidate).split("\\s+"))
                .filter(token -> !token.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        return Arrays.stream(authoritativeBase.split("\\s+"))
                .filter(token -> !token.isBlank())
                .filter(token -> !UNPROTECTED_CONNECTORS.contains(token))
                .allMatch(candidateTokens::contains);
    }

    private QueryIntentResponse parseResponse(String response) {
        try {
            QueryIntentResponse parsed = objectMapper.readValue(
                    OpenRouterJsonExtractor.objectCandidate(response),
                    QueryIntentResponse.class);
            return parsed == null ? new QueryIntentResponse(null, null, List.of(), List.of(), LOW) : parsed;
        } catch (JacksonException exception) {
            QueryIntentResponse loose = parseLooseResponse(response);
            if (loose != null) {
                return loose;
            }
            throw new OpenRouterException("OpenRouter returned invalid product search query JSON", exception);
        }
    }

    private QueryIntentResponse parseLooseResponse(String response) {
        Map<String, String> values = OpenRouterJsonExtractor.looseKeyValues(response, RESPONSE_KEYS);
        if (values.isEmpty()) {
            return null;
        }
        return new QueryIntentResponse(
                values.get("searchQuery"),
                values.get("displayQuery"),
                looseList(values.get("constraints")),
                looseList(values.get("preferenceHints")),
                values.get("confidence")
        );
    }

    private List<String> looseList(String value) {
        String cleaned = OpenRouterJsonExtractor.cleanLooseValue(value);
        if (cleaned == null || cleaned.isBlank() || "[]".equals(cleaned)) {
            return List.of();
        }
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        if (cleaned.isBlank()) {
            return List.of();
        }
        return Arrays.stream(cleaned.split("[,;]"))
                .map(OpenRouterJsonExtractor::cleanLooseValue)
                .filter(item -> item != null && !item.isBlank())
                .toList();
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

    private String sanitizeDisplayQuery(String displayQuery, String normalizedSearchQuery) {
        if (displayQuery == null || displayQuery.isBlank()) {
            return displayQuery(normalizedSearchQuery);
        }
        String sanitized = displayQuery
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("^['\"“”‘’]+|['\"“”‘’]+$", "")
                .trim();
        if (sanitized.length() < 3 || sanitized.length() > 80) {
            return displayQuery(normalizedSearchQuery);
        }
        return sanitized;
    }

    private String displayQuery(String normalizedSearchQuery) {
        String normalized = userProductSearchHashService.normalizeQuery(normalizedSearchQuery);
        if (normalized.isBlank()) {
            return "Products";
        }
        String[] tokens = normalized.split("\\s+");
        for (int index = 0; index < tokens.length; index++) {
            tokens[index] = displayToken(tokens[index], index == 0);
        }
        return String.join(" ", tokens);
    }

    private String displayToken(String token, boolean first) {
        return switch (token) {
            case "usd" -> "USD";
            case "usb-c" -> "USB-C";
            case "usb-a" -> "USB-A";
            case "hdmi" -> "HDMI";
            case "t-shirt", "tee-shirt" -> "T-shirt";
            default -> first ? capitalize(token) : token;
        };
    }

    private String capitalize(String value) {
        if (value.isBlank()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private record QueryIntentResponse(
            String searchQuery,
            String displayQuery,
            List<String> constraints,
            List<String> preferenceHints,
            String confidence
    ) {
    }
}
