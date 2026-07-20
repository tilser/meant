package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.service.dto.AgentResolvedReadIntent;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentReadIntentResolverTest {

    private final AgentMutationTargetPolicy targetPolicy = mock(AgentMutationTargetPolicy.class);
    private final AgentJsonSupport jsonSupport = mock(AgentJsonSupport.class);
    private final AgentReadIntentResolver resolver = new AgentReadIntentResolver(
            targetPolicy,
            jsonSupport,
            new ObjectMapper()
    );

    @Test
    void resolvesTheExactSimilarityFollowUpToTheVerifiedThirdProduct() {
        AgentToolExecutionContext context = context(
                "I like the third one, can you find some similar like those?"
        );
        when(targetPolicy.explicitProductTarget(context)).thenReturn(Optional.of(
                new AgentVisibleProductReference(
                        3,
                        3,
                        "swim-shorts-packing-pouch",
                        "offer-3",
                        "Swim Shorts with Packing Pouch"
                )
        ));
        when(jsonSupport.writeArtifact(any())).thenReturn(
                "{\"canonicalProductKey\":\"swim-shorts-packing-pouch\","
                        + "\"query\":\"products similar to Swim Shorts with Packing Pouch\"}"
        );

        Optional<AgentResolvedReadIntent> resolved = resolver.resolve(context);

        assertThat(resolved).hasValueSatisfying(intent -> {
            assertThat(intent.subject()).isEqualTo("Swim Shorts with Packing Pouch");
            assertThat(intent.toolCall().name()).isEqualTo("find_similar_products");
            assertThat(intent.toolCall().argumentsJson()).contains(
                    "\"canonicalProductKey\":\"swim-shorts-packing-pouch\"",
                    "products similar to Swim Shorts with Packing Pouch"
            );
        });
    }

    @Test
    void doesNotRouteAStatementThatExplicitlyNegatesSimilaritySearch() {
        AgentToolExecutionContext context = context(
                "Don't find anything similar to the third one."
        );

        assertThat(resolver.resolve(context)).isEmpty();
        verifyNoInteractions(targetPolicy, jsonSupport);
    }

    @Test
    void doesNotTreatADescriptiveComparisonAsASearchRequest() {
        AgentToolExecutionContext context = context(
                "I find the third one similar to another product I own."
        );

        assertThat(resolver.resolve(context)).isEmpty();
        verifyNoInteractions(targetPolicy, jsonSupport);
    }

    @Test
    void leavesACompoundSimilarityAndCartRequestToTheModelLoop() {
        AgentToolExecutionContext context = context(
                "Can you find products similar to the third one and add the best one to my cart?"
        );

        assertThat(resolver.resolve(context)).isEmpty();
        verifyNoInteractions(targetPolicy, jsonSupport);
    }

    @Test
    void leavesACompoundReviewsAndSimilarityRequestToTheModelLoop() {
        AgentToolExecutionContext context = context(
                "Find reviews for the third one and similar products."
        );

        assertThat(resolver.resolve(context)).isEmpty();
        verifyNoInteractions(targetPolicy, jsonSupport);
    }

    @Test
    void reportsWhetherTheVerifiedSimilaritySearchFoundProducts() {
        AgentResolvedReadIntent intent = new AgentResolvedReadIntent(
                new com.meant.api.module.agent.service.dto.AgentModelToolCall(
                        null,
                        "find_similar_products",
                        "{}"
                ),
                "Swim Shorts with Packing Pouch"
        );

        assertThat(resolver.completionMessage(
                intent,
                "{\"products\":[],\"nextOffset\":null,\"hasMore\":false,\"upstreamTruncated\":false,"
                        + "\"unavailableCanonicalProductKeys\":[]}"
        ))
                .isEqualTo("I couldn't find another product similar to Swim Shorts with Packing Pouch.");
        assertThat(resolver.completionMessage(
                intent,
                "{\"products\":[{\"reference\":1,\"canonicalProductKey\":\"similar-1\","
                        + "\"title\":\"Similar shorts\",\"description\":null,\"imageUrl\":null,"
                        + "\"recommendedOfferKey\":null,\"offers\":[]}],\"nextOffset\":null,"
                        + "\"hasMore\":false,\"upstreamTruncated\":false,"
                        + "\"unavailableCanonicalProductKeys\":[]}"
        )).isEqualTo("I found these products similar to Swim Shorts with Packing Pouch:");
        assertThat(resolver.completionMessage(intent, "not-json"))
                .isEqualTo("I searched for products similar to Swim Shorts with Packing Pouch:");
    }

    private AgentToolExecutionContext context(String turn) {
        return new AgentToolExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                turn
        );
    }
}
