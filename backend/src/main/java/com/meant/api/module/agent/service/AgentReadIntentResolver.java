package com.meant.api.module.agent.service;

import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentProductListResult;
import com.meant.api.module.agent.service.dto.AgentResolvedReadIntent;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import java.util.Optional;
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

    private final AgentMutationTargetPolicy mutationTargetPolicy;
    private final AgentJsonSupport jsonSupport;
    private final ObjectMapper objectMapper;

    public Optional<AgentResolvedReadIntent> resolve(AgentToolExecutionContext context) {
        String turn = context == null ? null : context.triggeringUserText();
        if (turn == null
                || !FIND_SIMILAR_INTENT.matcher(turn).find()
                || NEGATED_FIND_INTENT.matcher(turn).find()
                || OTHER_TOOL_INTENT.matcher(turn).find()) {
            return Optional.empty();
        }
        return mutationTargetPolicy.explicitProductTarget(context).map(product -> resolvedSimilarity(product));
    }

    public String completionMessage(AgentResolvedReadIntent intent, String resultJson) {
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
        return "I couldn't load products similar to " + intent.subject() + " right now. Please try again.";
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

    private String displayTitle(AgentVisibleProductReference product) {
        String title = product.title() == null || product.title().isBlank()
                ? "the selected product"
                : product.title().replaceAll("\\s+", " ").trim();
        return title.length() <= MAXIMUM_SUBJECT_LENGTH
                ? title
                : title.substring(0, MAXIMUM_SUBJECT_LENGTH - 1).stripTrailing() + "…";
    }

    private record SimilarProductArguments(
            String canonicalProductKey,
            String query
    ) {
    }
}
