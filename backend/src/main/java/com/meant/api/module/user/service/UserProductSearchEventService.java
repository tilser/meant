package com.meant.api.module.user.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.entity.UserProductSearchEvent;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.repository.UserProductSearchEventRepository;
import com.meant.api.module.user.service.dto.UserPopularProductSearchResult;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserProductSearchEventService {

    private static final String POPULAR_SEARCH_POLISH_SYSTEM_PROMPT = """
            You rewrite aggregate shopping search trends into safe public-facing search suggestions.
            Inputs are already aggregate popular searches, but may still contain awkward wording or personal details.
            Return short, appealing product-search phrases people would want to tap.
            Make wording specific, polished, and commercially attractive without sounding like an ad.
            Keep output order aligned with the input popularity order unless merging duplicates.
            Anonymize aggressively: remove names, exact locations, personal recipients, personal pronouns,
            contact details, medical conditions, IDs, and anything identifying.
            Never include first-person phrasing such as my, me, mine, or a named recipient.
            Keep concrete product nouns and useful shopping constraints such as budget, material, size, scent,
            compatibility, dietary constraint, or occasion.
            Do not invent unrelated product categories that are not implied by the candidates.
            Prefer concise title/sentence casing.
            """;
    private static final Pattern FIRST_PERSON_QUERY_PATTERN = Pattern.compile(
            "\\b(i|me|my|mine|myself)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PRIVATE_QUERY_PATTERN = Pattern.compile(
            "(@|\\b\\d{3}[-. ]?\\d{2}[-. ]?\\d{4}\\b|\\b\\d{10,}\\b|\\b(password|ssn|social security)\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private final UserProductSearchEventRepository userProductSearchEventRepository;
    private final UserProductSearchProperties userProductSearchProperties;
    private final OpenRouterProperties openRouterProperties;
    private final OpenRouterChatClient openRouterChatClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(
            UUID userId,
            UUID merchantId,
            UserProductSearchQueryIntentResult queryIntent,
            int resultCount,
            Instant now
    ) {
        userProductSearchEventRepository.save(UserProductSearchEvent.from(
                userId,
                merchantId,
                openRouterProperties.models().productSearchQueryParser(),
                userProductSearchProperties.queryParserPromptVersion(),
                queryIntent,
                resultCount,
                now
        ));
    }

    public List<UserPopularProductSearchResult> popular(Instant now) {
        LinkedHashMap<String, UserPopularProductSearchResult> results = new LinkedHashMap<>();
        appendPopular(results, now.minus(userProductSearchProperties.popularSearchWindow()));
        if (results.size() < userProductSearchProperties.popularSearchLimit()) {
            appendPopular(results, now.minus(userProductSearchProperties.popularSearchFallbackWindow()));
        }
        List<UserPopularProductSearchResult> aggregateResults = results.values().stream()
                .limit(userProductSearchProperties.popularSearchLimit())
                .toList();
        if (aggregateResults.isEmpty()) {
            return List.of();
        }
        return polish(aggregateResults);
    }

    private void appendPopular(
            LinkedHashMap<String, UserPopularProductSearchResult> results,
            Instant since
    ) {
        userProductSearchEventRepository.findPopularSearches(
                        since,
                        userProductSearchProperties.popularSearchMinDistinctUsers(),
                        userProductSearchProperties.popularSearchMaxDisplayLength(),
                        PageRequest.of(0, userProductSearchProperties.popularSearchLimit() * 2)
                )
                .stream()
                .filter(this::safeForDisplay)
                .forEach(result -> results.putIfAbsent(
                        result.displayQuery().toLowerCase(Locale.ROOT),
                        result
                ));
    }

    private boolean safeForDisplay(UserPopularProductSearchResult result) {
        String displayQuery = result.displayQuery();
        return displayQuery != null
                && !displayQuery.isBlank()
                && displayQuery.length() <= userProductSearchProperties.popularSearchMaxDisplayLength()
                && !PRIVATE_QUERY_PATTERN.matcher(displayQuery).find();
    }

    private List<UserPopularProductSearchResult> polish(List<UserPopularProductSearchResult> aggregateResults) {
        try {
            String response = openRouterChatClient.completeJson(
                    openRouterProperties.models().productSearchQueryParser(),
                    POPULAR_SEARCH_POLISH_SYSTEM_PROMPT,
                    popularSearchPolishPrompt(aggregateResults),
                    "popular_product_searches",
                    popularSearchPolishSchema()
            );
            return sanitizePolishedResults(parsePolishedResults(response));
        } catch (OpenRouterException | JacksonException exception) {
            log.warn("Could not polish popular product searches: {}", exception.getMessage());
            return List.of();
        }
    }

    private String popularSearchPolishPrompt(
            List<UserPopularProductSearchResult> aggregateResults
    ) throws JacksonException {
        List<PopularSearchCandidate> candidates = aggregateResults.stream()
                .map(result -> new PopularSearchCandidate(
                        result.displayQuery(),
                        result.searchCount(),
                        result.distinctUserCount()
                ))
                .toList();
        return "Return up to " + userProductSearchProperties.popularSearchLimit()
                + " polished searches for these aggregate candidates:\n"
                + objectMapper.writeValueAsString(candidates);
    }

    private OpenRouterJsonSchemaDefinition popularSearchPolishSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("searches"),
                Map.of(
                        "searches",
                        OpenRouterJsonSchemaDefinition.array(
                                OpenRouterJsonSchemaDefinition.object(
                                        List.of("displayQuery", "query"),
                                        Map.of(
                                                "displayQuery", OpenRouterJsonSchemaDefinition.string(),
                                                "query", OpenRouterJsonSchemaDefinition.string()
                                        )
                                ),
                                0,
                                userProductSearchProperties.popularSearchLimit()
                        )
                )
        );
    }

    private PopularSearchPolishResponse parsePolishedResults(String response) throws JacksonException {
        PopularSearchPolishResponse parsed = objectMapper.readValue(response, PopularSearchPolishResponse.class);
        return parsed == null ? new PopularSearchPolishResponse(List.of()) : parsed;
    }

    private List<UserPopularProductSearchResult> sanitizePolishedResults(PopularSearchPolishResponse response) {
        if (response.searches() == null || response.searches().isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, UserPopularProductSearchResult> deduped = new LinkedHashMap<>();
        for (PopularSearchPolishItem item : response.searches()) {
            if (deduped.size() >= userProductSearchProperties.popularSearchLimit()) {
                break;
            }
            String displayQuery = sanitizePolishedQuery(item.displayQuery());
            String query = sanitizePolishedQuery(item.query());
            if (displayQuery == null) {
                continue;
            }
            if (query == null) {
                query = displayQuery;
            }
            deduped.putIfAbsent(
                    displayQuery.toLowerCase(Locale.ROOT),
                    new UserPopularProductSearchResult(displayQuery, query, null, null, null)
            );
        }
        return new ArrayList<>(deduped.values());
    }

    private String sanitizePolishedQuery(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("^['\"“”‘’]+|['\"“”‘’]+$", "")
                .trim();
        if (sanitized.length() < 3
                || sanitized.length() > userProductSearchProperties.popularSearchMaxDisplayLength()
                || FIRST_PERSON_QUERY_PATTERN.matcher(sanitized).find()
                || PRIVATE_QUERY_PATTERN.matcher(sanitized).find()) {
            return null;
        }
        return sanitized;
    }

    private record PopularSearchCandidate(
            String displayQuery,
            Long searchCount,
            Long distinctUserCount
    ) {
    }

    private record PopularSearchPolishResponse(
            List<PopularSearchPolishItem> searches
    ) {
    }

    private record PopularSearchPolishItem(
            String displayQuery,
            String query
    ) {
    }
}
