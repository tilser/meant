package com.meant.api.module.agent.service;

import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentProductListResult;
import com.meant.api.module.agent.service.dto.AgentResolvedReadIntent;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import com.meant.api.module.agent.service.query.ResolveAgentPendingProductSearchQuery;
import com.meant.api.module.user.service.UserProductSearchCategoryPolicy;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Routes narrow, server-verifiable read intents without relying on a model to repeat trusted IDs. */
@Service
@RequiredArgsConstructor
public class AgentReadIntentResolver {

    private static final String SIMILAR_TOOL = "find_similar_products";
    private static final String SEARCH_TOOL = "search_catalog";
    private static final int MAXIMUM_ROUTING_QUERY_LENGTH = 500;
    private static final int MAXIMUM_SUBJECT_LENGTH = 120;
    private static final Pattern FIND_SIMILAR_INTENT = Pattern.compile(
            "(?:(?:^|[,.!?;]\\s*)(?:please\\s+)?"
                    + "|\\b(?:can|could|would|will)\\s+you\\s+(?:please\\s+)?)"
                    + "(?:find|show|search(?:\\s+for)?|look\\s+for|get|give)\\b"
                    + "[^.!?]{0,120}\\bsimilar\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NEGATED_FIND_INTENT = Pattern.compile(
            "\\b(?:don['’]?t|dont|do\\s+not|never)\\b[^.!?]{0,80}"
                    + "\\b(?:find|show|search|get|similar)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OTHER_TOOL_INTENT = Pattern.compile(
            "\\b(?:add|put|place|buy|purchase|order|pin|save|unpin|remove|delete|take\\s+out|"
                    + "watch|unwatch|change|update|increase|decrease|set|apply|checkout|check\\s+out|"
                    + "compare|pick|choose|select|about|details?|inspect|reviews?|codes?|coupons?|"
                    + "discounts?|promos?|list|load|read|orders?|carts?|inventory|preferences?)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRUSTED_FOLLOW_ON_ACTION = Pattern.compile(
            "(?iu)(?:[,;]\\s*|\\s+(?:and(?:\\s+then)?|then)\\s+)"
                    + "(?<action>(?:please\\s+)?(?:"
                    + "add|put|place|buy|purchase|order|pin|save|remove|delete|"
                    + "checkout|check\\s+out|build\\s+(?:a\\s+)?cart|"
                    + "compare|pick|choose|select|inspect|review|read|show|tell|get|find"
                    + ")\\b.*)$"
    );
    private static final Pattern EXPLICIT_PRODUCT_SEARCH_INTENT = Pattern.compile(
            "(?iu)^\\s*(?:please\\s+)?(?:"
                    + "(?:(?:can|could|would|will)\\s+you\\s+(?:please\\s+)?)?"
                    + "(?:find|show(?:\\s+me)?|search(?:\\s+for)?|look\\s+for|get\\s+me|"
                    + "recommend|suggest|shop\\s+for)\\b"
                    + "|(?:i(?:'m|\\s+am)?\\s+)?looking\\s+for\\b"
                    + "|i\\s+(?:want|need)\\b"
                    + "|i(?:['’]d|\\s+would)\\s+like\\b"
                    + ")"
    );
    private static final Pattern BARE_PRODUCT_QUERY_DISQUALIFIER = Pattern.compile(
            "(?iu)\\b(?:am|are|bought|can|could|did|do|does|explain|hate|how|is|like|love|"
                    + "own|remember|say|saw|tell|thank|thanks|think|was|wear|were|what|when|"
                    + "where|which|who|why|will|would|write)\\b"
    );
    private static final Pattern NON_PRODUCT_TOPIC = Pattern.compile(
            "(?iu)\\b(?:joke|weather|return\\s+policy|refund\\s+policy|privacy\\s+policy|"
                    + "terms\\s+(?:and|of)|customer\\s+service|password|help|support)\\b"
    );
    private static final Set<String> NON_PRODUCT_BARE_HEADS = Set.of(
            "address", "advice", "afternoon", "chat", "conversation", "day", "evening", "help",
            "idea", "issue", "joke", "life", "message", "morning", "news", "night", "opinion",
            "order", "poem", "problem", "question", "song", "status", "story", "support", "thanks",
            "thought", "weather"
    );
    private static final int MAXIMUM_BARE_PRODUCT_QUERY_WORDS = 12;

    private final AgentMutationTargetPolicy mutationTargetPolicy;
    private final AgentProductSearchQualificationService productSearchQualificationService;
    private final UserProductSearchCategoryPolicy productSearchCategoryPolicy;
    private final AgentJsonSupport jsonSupport;
    private final ObjectMapper objectMapper;

    public Optional<AgentResolvedReadIntent> resolve(AgentToolExecutionContext context) {
        String turn = context == null ? null : context.triggeringUserText();
        Optional<InitialSearchIntent> initialSearch = initialSearchIntent(turn);
        if (initialSearch.isPresent()) {
            return Optional.of(resolvedInitialSearch(initialSearch.get()));
        }
        if (context != null
                && turn != null
                && !turn.isBlank()) {
            Optional<AgentResolvedReadIntent> continuation = productSearchQualificationService
                    .resolvePendingContinuation(new ResolveAgentPendingProductSearchQuery(
                            context.userId(),
                            context.conversationId(),
                            context.merchantId(),
                            context.triggeringMessageId(),
                            turn
                    ))
                    .map(pending -> resolvedSearchContinuation(
                            pending.qualificationId(),
                            pending.originalQuery(),
                            pending.observedUpdatedAt(),
                            turn
                    ));
            if (continuation.isPresent()) {
                return continuation;
            }
        }
        return resolvedSimilarity(context, turn);
    }

    public String completionMessage(AgentResolvedReadIntent intent, String resultJson) {
        if (SEARCH_TOOL.equals(intent.toolCall().name())) {
            return searchCompletionMessage(resultJson);
        }
        try {
            AgentProductListResult result = objectMapper.readValue(resultJson, AgentProductListResult.class);
            if (result == null || result.products().isEmpty()) {
                return "I couldn't find another product similar to " + intent.subject() + ".";
            }
        } catch (JacksonException exception) {
            return "I searched for products similar to " + intent.subject() + ":";
        }
        return "I found these products similar to " + intent.subject() + ":";
    }

    public String failureMessage(AgentResolvedReadIntent intent) {
        if (SEARCH_TOOL.equals(intent.toolCall().name())) {
            return "I couldn't complete that product search right now. Please try again.";
        }
        return "I couldn't load products similar to " + intent.subject() + " right now. Please try again.";
    }

    private String searchCompletionMessage(String resultJson) {
        try {
            AgentProductListResult result = objectMapper.readValue(resultJson, AgentProductListResult.class);
            if (result == null || result.products().isEmpty()) {
                return "I couldn't find a product matching those requirements.";
            }
        } catch (JacksonException exception) {
            return "I searched with those requirements:";
        }
        return "I found these options:";
    }

    private Optional<AgentResolvedReadIntent> resolvedSimilarity(
            AgentToolExecutionContext context,
            String turn
    ) {
        if (context == null
                || turn == null
                || !FIND_SIMILAR_INTENT.matcher(turn).find()
                || NEGATED_FIND_INTENT.matcher(turn).find()
                || OTHER_TOOL_INTENT.matcher(turn).find()) {
            return Optional.empty();
        }
        return mutationTargetPolicy.explicitProductTarget(context).map(this::resolvedSimilarity);
    }

    private Optional<InitialSearchIntent> initialSearchIntent(String turn) {
        if (turn == null
                || turn.isBlank()
                || NEGATED_FIND_INTENT.matcher(turn).find()
                || FIND_SIMILAR_INTENT.matcher(turn).find()
                || NON_PRODUCT_TOPIC.matcher(turn).find()) {
            return Optional.empty();
        }
        Optional<TrustedFollowOn> followOn = trustedFollowOn(turn);
        String searchClause = followOn.map(TrustedFollowOn::searchClause).orElse(turn).trim();
        boolean explicit = EXPLICIT_PRODUCT_SEARCH_INTENT.matcher(searchClause).find();
        if (!explicit && followOn.isPresent()) {
            return Optional.empty();
        }
        if (OTHER_TOOL_INTENT.matcher(searchClause).find()) {
            return Optional.empty();
        }
        Optional<UserProductSearchCategoryPolicy.ProductSubject> subject =
                productSearchCategoryPolicy.productSubject(searchClause);
        if (subject.isEmpty()) {
            return Optional.empty();
        }
        if (explicit) {
            return NON_PRODUCT_BARE_HEADS.contains(subject.get().head())
                    ? Optional.empty()
                    : Optional.of(new InitialSearchIntent(
                            turn.trim(),
                            followOn.map(TrustedFollowOn::actionClause).orElse(null)
                    ));
        }
        if (searchClause.contains("?")
                || BARE_PRODUCT_QUERY_DISQUALIFIER.matcher(searchClause).find()
                || searchClause.split("\\s+").length > MAXIMUM_BARE_PRODUCT_QUERY_WORDS
                || NON_PRODUCT_BARE_HEADS.contains(subject.get().head())) {
            return Optional.empty();
        }
        return productSearchCategoryPolicy.category(searchClause, searchClause)
                        == UserProductSearchCategoryPolicy.Category.OTHER
                ? Optional.empty()
                : Optional.of(new InitialSearchIntent(turn.trim(), null));
    }

    private Optional<TrustedFollowOn> trustedFollowOn(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        var matcher = TRUSTED_FOLLOW_ON_ACTION.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String searchClause = value.substring(0, matcher.start()).trim();
        String actionClause = matcher.group("action").trim();
        if (searchClause.isBlank() || actionClause.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new TrustedFollowOn(searchClause, actionClause));
    }

    private AgentResolvedReadIntent resolvedInitialSearch(InitialSearchIntent search) {
        String query = search.query();
        String argumentsJson = jsonSupport.writeArtifact(new InitialSearchArguments(routingQuery(query)));
        return new AgentResolvedReadIntent(
                new AgentModelToolCall(null, SEARCH_TOOL, argumentsJson),
                displaySubject(query),
                search.trustedFollowOnAction()
        );
    }

    private AgentResolvedReadIntent resolvedSimilarity(AgentVisibleProductReference product) {
        String subject = displayTitle(product);
        String argumentsJson = jsonSupport.writeArtifact(new SimilarProductArguments(
                product.canonicalProductKey(),
                "products similar to " + subject
        ));
        return new AgentResolvedReadIntent(
                new AgentModelToolCall(null, SIMILAR_TOOL, argumentsJson),
                subject
        );
    }

    private AgentResolvedReadIntent resolvedSearchContinuation(
            UUID qualificationId,
            String originalQuery,
            Instant qualificationUpdatedAt,
            String currentTurn
    ) {
        String argumentsJson = jsonSupport.writeArtifact(new SearchContinuationArguments(
                routingQuery(originalQuery),
                qualificationId,
                qualificationUpdatedAt
        ));
        return new AgentResolvedReadIntent(
                new AgentModelToolCall(null, SEARCH_TOOL, argumentsJson),
                displaySubject(originalQuery),
                trustedFollowOn(currentTurn)
                        .or(() -> trustedFollowOn(originalQuery))
                        .map(TrustedFollowOn::actionClause)
                        .orElse(null)
        );
    }

    private String displayTitle(AgentVisibleProductReference product) {
        String title = product.title() == null || product.title().isBlank()
                ? "the selected product"
                : product.title().replaceAll("\\s+", " ").trim();
        return displaySubject(title);
    }

    private String routingQuery(String value) {
        return bounded(value.trim(), MAXIMUM_ROUTING_QUERY_LENGTH);
    }

    private String displaySubject(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAXIMUM_SUBJECT_LENGTH) {
            return normalized;
        }
        return bounded(normalized, MAXIMUM_SUBJECT_LENGTH - 1).stripTrailing() + "…";
    }

    private String bounded(String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }
        int end = maximumLength;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end -= 1;
        }
        return value.substring(0, end);
    }

    private record SimilarProductArguments(
            String canonicalProductKey,
            String query
    ) {
    }

    private record SearchContinuationArguments(
            String query,
            UUID qualificationId,
            Instant qualificationUpdatedAt
    ) {
    }

    private record InitialSearchArguments(String query) {
    }

    private record InitialSearchIntent(
            String query,
            String trustedFollowOnAction
    ) {
    }

    private record TrustedFollowOn(
            String searchClause,
            String actionClause
    ) {
    }
}
