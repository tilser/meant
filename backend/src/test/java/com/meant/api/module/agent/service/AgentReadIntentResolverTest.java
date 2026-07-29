package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.service.dto.AgentResolvedReadIntent;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import com.meant.api.module.agent.service.dto.AgentPendingProductSearchContinuation;
import com.meant.api.module.agent.service.query.ResolveAgentPendingProductSearchQuery;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.UserProductSearchCategoryPolicy;
import com.meant.api.module.user.service.UserProductSearchQualificationPersistenceService;
import com.meant.api.module.user.service.UserProductSearchQualificationPlanMapper;
import com.meant.api.module.user.service.UserProductSearchQualificationService;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationSnapshot;
import com.meant.api.module.user.service.query.FindUserProductSearchQualificationByRequestQuery;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentReadIntentResolverTest {

    private final AgentMutationTargetPolicy targetPolicy = mock(AgentMutationTargetPolicy.class);
    private final AgentProductSearchQualificationService qualificationService =
            mock(AgentProductSearchQualificationService.class);
    private final AgentJsonSupport jsonSupport = mock(AgentJsonSupport.class);
    private final AgentReadIntentResolver resolver = new AgentReadIntentResolver(
            targetPolicy,
            qualificationService,
            new UserProductSearchCategoryPolicy(),
            jsonSupport,
            new ObjectMapper()
    );

    @Test
    void clipsAnEightThousandCharacterInitialRoutingQueryWithoutLosingTheSearchIntent() throws Exception {
        String turn = boundedTurn("Find cool running shoes ");
        AgentToolExecutionContext context = context(turn);
        AgentReadIntentResolver resolverWithRealJson = resolverWithRealJson();

        AgentResolvedReadIntent resolved = resolverWithRealJson.resolve(context).orElseThrow();

        String routedQuery = new ObjectMapper()
                .readTree(resolved.toolCall().argumentsJson())
                .get("query")
                .asText();
        assertThat(routedQuery)
                .hasSize(500)
                .isEqualTo(turn.substring(0, 500));
        assertThat(resolved.toolCall().argumentsJson())
                .doesNotContain("qualificationId", "qualificationUpdatedAt");
        assertThat(resolved.subject()).hasSize(120).endsWith("…");
    }

    @Test
    void clipsALongPersistedOriginalOnlyInThePendingRoutingArgument() throws Exception {
        String originalQuery = boundedTurn("Find cool running shoes ");
        AgentToolExecutionContext context = context("46");
        UUID qualificationId = UUID.randomUUID();
        Instant observedUpdatedAt = Instant.parse("2026-07-29T12:00:00Z");
        when(qualificationService.resolvePendingContinuation(new ResolveAgentPendingProductSearchQuery(
                context.userId(),
                context.conversationId(),
                context.merchantId(),
                context.triggeringMessageId(),
                "46"
        ))).thenReturn(Optional.of(new AgentPendingProductSearchContinuation(
                qualificationId,
                originalQuery,
                observedUpdatedAt
        )));
        AgentReadIntentResolver resolverWithRealJson = resolverWithRealJson();

        AgentResolvedReadIntent resolved = resolverWithRealJson.resolve(context).orElseThrow();

        var arguments = new ObjectMapper().readTree(resolved.toolCall().argumentsJson());
        assertThat(arguments.get("query").asText())
                .hasSize(500)
                .isEqualTo(originalQuery.substring(0, 500));
        assertThat(arguments.get("qualificationId").asText())
                .isEqualTo(qualificationId.toString());
        assertThat(arguments.get("qualificationUpdatedAt").asText())
                .isEqualTo(observedUpdatedAt.toString());
        assertThat(resolved.subject()).hasSize(120).endsWith("…");
    }

    @Test
    void routesABareProductSearchToServerQualificationBeforeTheGeneralAgentModel() {
        AgentToolExecutionContext context = context("cool running shoes");
        when(jsonSupport.writeArtifact(any())).thenReturn("{\"query\":\"cool running shoes\"}");

        Optional<AgentResolvedReadIntent> resolved = resolver.resolve(context);

        assertThat(resolved).hasValueSatisfying(intent -> {
            assertThat(intent.subject()).isEqualTo("cool running shoes");
            assertThat(intent.toolCall().name()).isEqualTo("search_catalog");
            assertThat(intent.toolCall().argumentsJson())
                    .isEqualTo("{\"query\":\"cool running shoes\"}")
                    .doesNotContain("qualificationId", "qualificationUpdatedAt");
        });
        verifyNoInteractions(qualificationService, targetPolicy);
    }

    @Test
    void leavesABareUnknownPhraseToTheGeneralAgent() {
        AgentToolExecutionContext context = context("ceramic yarn bowl");

        assertThat(resolver.resolve(context)).isEmpty();

        verify(qualificationService).resolvePendingContinuation(any());
        verifyNoInteractions(targetPolicy, jsonSupport);
    }

    @Test
    void routesSearchWithACartFollowOnThroughQualificationFirst() {
        String turn = "find running shoes and add the best pair to my cart";
        AgentToolExecutionContext context = context(turn);
        when(jsonSupport.writeArtifact(any())).thenReturn(
                "{\"query\":\"find running shoes and add the best pair to my cart\"}"
        );

        assertThat(resolver.resolve(context)).hasValueSatisfying(intent -> {
            assertThat(intent.toolCall().name()).isEqualTo("search_catalog");
            assertThat(intent.toolCall().argumentsJson()).contains("find running shoes");
            assertThat(intent.continueWithModelAfterSuccess()).isTrue();
            assertThat(intent.trustedFollowOnAction())
                    .isEqualTo("add the best pair to my cart");
        });
        verifyNoInteractions(qualificationService, targetPolicy);
    }

    @Test
    void routesAReadCompoundSearchFirstAndPreservesItsExactFollowOnClause() {
        String turn = "find running shoes and compare the best options";
        when(jsonSupport.writeArtifact(any())).thenReturn("{\"query\":\"" + turn + "\"}");

        assertThat(resolver.resolve(context(turn))).hasValueSatisfying(intent -> {
            assertThat(intent.toolCall().name()).isEqualTo("search_catalog");
            assertThat(intent.trustedFollowOnAction()).isEqualTo("compare the best options");
        });
        verifyNoInteractions(qualificationService, targetPolicy);
    }

    @Test
    void routesAConversationalDesireForAnUnknownProduct() {
        String turn = "I'd like a ceramic yarn bowl";
        when(jsonSupport.writeArtifact(any())).thenReturn("{\"query\":\"" + turn + "\"}");

        assertThat(resolver.resolve(context(turn))).hasValueSatisfying(intent -> {
            assertThat(intent.toolCall().name()).isEqualTo("search_catalog");
            assertThat(intent.trustedFollowOnAction()).isNull();
        });
        verifyNoInteractions(qualificationService, targetPolicy);
    }

    @Test
    void routesAnExplicitUnknownProductPhraseToServerQualification() {
        AgentToolExecutionContext context = context("Find me a ceramic yarn bowl");
        when(jsonSupport.writeArtifact(any())).thenReturn(
                "{\"query\":\"Find me a ceramic yarn bowl\"}"
        );

        assertThat(resolver.resolve(context)).hasValueSatisfying(intent -> {
            assertThat(intent.toolCall().name()).isEqualTo("search_catalog");
            assertThat(intent.toolCall().argumentsJson())
                    .isEqualTo("{\"query\":\"Find me a ceramic yarn bowl\"}");
        });
        verifyNoInteractions(targetPolicy);
    }

    @Test
    void leavesGeneralQuestionsAndNonSearchProseToTheGeneralAgent() {
        for (String turn : java.util.List.of(
                "tell me a joke",
                "what is the return policy?",
                "running shoes are fascinating",
                "I love running shoes",
                "beautiful sunny day",
                "thanks"
        )) {
            assertThat(resolver.resolve(context(turn))).as(turn).isEmpty();
        }
        verifyNoInteractions(targetPolicy, jsonSupport);
    }

    @Test
    void leavesDetailReviewCartAndCompoundSearchTurnsToTheGeneralAgent() {
        for (String turn : java.util.List.of(
                "Show me details about the running shoes",
                "Find reviews for running shoes",
                "Compare these running shoes"
        )) {
            assertThat(resolver.resolve(context(turn))).as(turn).isEmpty();
        }
        verifyNoInteractions(targetPolicy, jsonSupport);
    }

    @Test
    void routesAPendingQualificationAnswerBeforeTheGeneralAgentModel() {
        AgentToolExecutionContext context = context("46");
        UUID qualificationId = UUID.randomUUID();
        Instant observedUpdatedAt = Instant.parse("2026-07-29T12:00:00Z");
        when(qualificationService.resolvePendingContinuation(new ResolveAgentPendingProductSearchQuery(
                context.userId(),
                context.conversationId(),
                context.merchantId(),
                context.triggeringMessageId(),
                "46"
        ))).thenReturn(Optional.of(new AgentPendingProductSearchContinuation(
                qualificationId,
                "cool running shoes",
                observedUpdatedAt
        )));
        when(jsonSupport.writeArtifact(any())).thenReturn(
                "{\"query\":\"cool running shoes\",\"qualificationId\":\""
                        + qualificationId
                        + "\",\"qualificationUpdatedAt\":\"2026-07-29T12:00:00Z\"}"
        );

        Optional<AgentResolvedReadIntent> resolved = resolver.resolve(context);

        assertThat(resolved).hasValueSatisfying(intent -> {
            assertThat(intent.subject()).isEqualTo("cool running shoes");
            assertThat(intent.toolCall().name()).isEqualTo("search_catalog");
            assertThat(intent.toolCall().argumentsJson())
                    .contains(
                            "cool running shoes",
                            qualificationId.toString(),
                            observedUpdatedAt.toString()
                    );
        });
        verifyNoInteractions(targetPolicy);
    }

    @Test
    void routesAnAlreadyReadyBoundAnswerWhenTheWorkerRetriesTheSameTrigger() throws Exception {
        AgentToolExecutionContext context = context("46");
        UUID qualificationId = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-07-29T12:00:00Z");
        AgentConversationRepository conversationRepository = mock(AgentConversationRepository.class);
        AgentConversation conversation = mock(AgentConversation.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        when(conversationRepository.findByIdAndUserId(context.conversationId(), context.userId()))
                .thenReturn(Optional.of(conversation));
        when(conversation.getMerchantId()).thenReturn(context.merchantId());
        when(persistenceService.findByRequest(new FindUserProductSearchQualificationByRequestQuery(
                context.userId(),
                context.conversationId(),
                context.merchantId(),
                context.triggeringMessageId(),
                "46"
        ))).thenReturn(Optional.of(new UserProductSearchQualificationSnapshot(
                qualificationId,
                context.userId(),
                context.conversationId(),
                context.merchantId(),
                "cool running shoes",
                UserProductSearchQualificationStatus.READY,
                mock(UserProductSearchQualificationPlan.class),
                "model",
                "prompt",
                updatedAt.minusSeconds(1),
                updatedAt
        )));
        UserProductSearchCategoryPolicy categoryPolicy = new UserProductSearchCategoryPolicy();
        AgentProductSearchQualificationService realQualificationService =
                new AgentProductSearchQualificationService(
                        conversationRepository,
                        mock(AgentMessageRepository.class),
                        mock(UserProductSearchQualificationService.class),
                        persistenceService,
                        mock(UserProductSearchQualificationPlanMapper.class),
                        new AgentProductSearchQualificationContinuationPolicy(categoryPolicy),
                        mock(UserProductSearchProperties.class),
                        mock(AgentProperties.class)
                );
        AgentReadIntentResolver retryResolver = new AgentReadIntentResolver(
                targetPolicy,
                realQualificationService,
                categoryPolicy,
                new AgentJsonSupport(new ObjectMapper(), mock(AgentProperties.class)),
                new ObjectMapper()
        );

        AgentResolvedReadIntent resolved = retryResolver.resolve(context).orElseThrow();

        var arguments = new ObjectMapper().readTree(resolved.toolCall().argumentsJson());
        assertThat(resolved.toolCall().name()).isEqualTo("search_catalog");
        assertThat(arguments.get("query").asText()).isEqualTo("cool running shoes");
        assertThat(arguments.get("qualificationId").asText()).isEqualTo(qualificationId.toString());
        assertThat(arguments.get("qualificationUpdatedAt").asText()).isEqualTo(updatedAt.toString());
        verifyNoInteractions(targetPolicy);
    }

    @Test
    void routesAPendingAnswerWithAFollowOnCartActionAndPreservesTheCurrentExactClause() {
        AgentToolExecutionContext context = context(
                "Size 46, then add the best one to my cart."
        );
        UUID qualificationId = UUID.randomUUID();
        Instant observedUpdatedAt = Instant.parse("2026-07-29T12:00:00Z");
        when(qualificationService.resolvePendingContinuation(any())).thenReturn(Optional.of(
                new AgentPendingProductSearchContinuation(
                        qualificationId,
                        "cool running shoes",
                        observedUpdatedAt
                )
        ));
        when(jsonSupport.writeArtifact(any())).thenReturn("{\"query\":\"cool running shoes\"}");

        assertThat(resolver.resolve(context)).hasValueSatisfying(intent -> {
            assertThat(intent.toolCall().name()).isEqualTo("search_catalog");
            assertThat(intent.trustedFollowOnAction())
                    .isEqualTo("add the best one to my cart.");
        });
        verify(qualificationService).resolvePendingContinuation(any());
        verifyNoInteractions(targetPolicy);
    }

    @Test
    void derivesThePersistedOriginalFollowOnAfterABarePendingAnswer() {
        AgentToolExecutionContext context = context("46");
        UUID qualificationId = UUID.randomUUID();
        Instant observedUpdatedAt = Instant.parse("2026-07-29T12:00:00Z");
        when(qualificationService.resolvePendingContinuation(any())).thenReturn(Optional.of(
                new AgentPendingProductSearchContinuation(
                        qualificationId,
                        "find running shoes and add the best pair to my cart",
                        observedUpdatedAt
                )
        ));
        when(jsonSupport.writeArtifact(any())).thenReturn("{\"query\":\"find running shoes\"}");

        assertThat(resolver.resolve(context)).hasValueSatisfying(intent ->
                assertThat(intent.trustedFollowOnAction())
                        .isEqualTo("add the best pair to my cart"));
        verifyNoInteractions(targetPolicy);
    }

    @Test
    void leavesAnUnrelatedTurnWithAPendingSearchToTheGeneralAgent() {
        AgentToolExecutionContext context = context("tell me a joke");
        ResolveAgentPendingProductSearchQuery query = new ResolveAgentPendingProductSearchQuery(
                context.userId(),
                context.conversationId(),
                context.merchantId(),
                context.triggeringMessageId(),
                "tell me a joke"
        );
        when(qualificationService.resolvePendingContinuation(query)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(context)).isEmpty();

        verify(qualificationService).resolvePendingContinuation(query);
        verifyNoInteractions(targetPolicy, jsonSupport);
    }

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

    @Test
    void reportsWhetherAResolvedQualificationSearchFoundProducts() {
        AgentResolvedReadIntent intent = new AgentResolvedReadIntent(
                new com.meant.api.module.agent.service.dto.AgentModelToolCall(
                        null,
                        "search_catalog",
                        "{}"
                ),
                "cool running shoes"
        );

        assertThat(resolver.completionMessage(
                intent,
                "{\"products\":[],\"nextOffset\":null,\"hasMore\":false,\"upstreamTruncated\":false}"
        )).isEqualTo("I couldn't find a product matching those requirements.");
        assertThat(resolver.completionMessage(
                intent,
                "{\"products\":[{\"reference\":1,\"canonicalProductKey\":\"shoe-1\","
                        + "\"title\":\"Shoe\",\"offers\":[]}],\"hasMore\":false,"
                        + "\"upstreamTruncated\":false}"
        )).isEqualTo("I found these options:");
        assertThat(resolver.failureMessage(intent))
                .isEqualTo("I couldn't complete that product search right now. Please try again.");
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

    private AgentReadIntentResolver resolverWithRealJson() {
        return new AgentReadIntentResolver(
                targetPolicy,
                qualificationService,
                new UserProductSearchCategoryPolicy(),
                new AgentJsonSupport(new ObjectMapper(), mock(AgentProperties.class)),
                new ObjectMapper()
        );
    }

    private String boundedTurn(String prefix) {
        return prefix + "x".repeat(8_000 - prefix.length());
    }
}
