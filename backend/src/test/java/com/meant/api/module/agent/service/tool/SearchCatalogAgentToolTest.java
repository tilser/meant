package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductSearchQualificationService;
import com.meant.api.module.agent.service.AgentSimilaritySearchQualificationService;
import com.meant.api.module.agent.service.command.QualifyAgentProductSearchCommand;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.dto.AgentProductSearchQualificationResult;
import com.meant.api.module.agent.service.dto.AgentSimilaritySearchQualificationContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.SearchCatalogAgentToolInput;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeName;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryCondition;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryLocation;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryPriceTier;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import com.meant.api.module.user.service.UserSimilarProductSearchService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.command.SearchSimilarUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SearchCatalogAgentToolTest {

    @Test
    void replaysAPreboundSimilarityQualificationWithTheExactAnchorAndReadyTypedFilters() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID triggeringMessageId = UUID.randomUUID();
        UUID qualificationId = QualifyUserProductSearchCommand.requestQualificationId(
                userId,
                conversationId,
                null,
                triggeringMessageId
        );
        UUID inventoryItemId = UUID.randomUUID();
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        AgentProductSearchQualificationService qualifications =
                mock(AgentProductSearchQualificationService.class);
        AgentSimilaritySearchQualificationService similarityQualifications =
                mock(AgentSimilaritySearchQualificationService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        UserSimilarProductSearchService similaritySearches = mock(UserSimilarProductSearchService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        CatalogDiscoveryFilters readyFilters = new CatalogDiscoveryFilters(
                true,
                List.of(),
                new CatalogDiscoveryLocation("US", "CA", "94107"),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(new CatalogDiscoveryAttributeFilter(
                        CatalogDiscoveryAttributeName.SIZE,
                        List.of("46")
                )),
                null,
                List.of()
        );
        SearchCatalogAgentToolInput input = new SearchCatalogAgentToolInput(
                "find products similar to my running shoes",
                null,
                null,
                null
        );
        AgentSimilaritySearchQualificationContext bound =
                new AgentSimilaritySearchQualificationContext(
                        qualificationId,
                        userId,
                        conversationId,
                        null,
                        "canonical:owned-running-shoes",
                        inventoryItemId,
                        "Cool Running Shoes",
                        "find products similar to my running shoes",
                        Instant.parse("2026-07-29T10:00:00Z")
                );
        UserGroupedProductSearchResult result = mock(UserGroupedProductSearchResult.class);
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class)).thenReturn(input);
        when(profiles.profile(userId)).thenReturn(profile);
        when(similarityQualifications.find(any())).thenReturn(java.util.Optional.of(bound));
        when(qualifications.qualify(any())).thenReturn(new AgentProductSearchQualificationResult(
                qualificationId,
                "running shoes",
                "Ready",
                List.of(),
                readyFilters
        ));
        when(result.products()).thenReturn(List.of());
        when(similaritySearches.search(eq(profile), any())).thenReturn(result);
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(
                json,
                profiles,
                results,
                qualifications,
                similarityQualifications,
                searches,
                similaritySearches
        );
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                userId,
                conversationId,
                UUID.randomUUID(),
                triggeringMessageId,
                "46, ship to 94107 in the US"
        ).withBuyerIp("203.0.113.42")
                .withUserAgent("Meant Browser/1.0")
                .withLanguage("cs-CZ");

        tool.execute(context, "{}");

        ArgumentCaptor<QualifyAgentProductSearchCommand> qualification =
                ArgumentCaptor.forClass(QualifyAgentProductSearchCommand.class);
        verify(qualifications).qualify(qualification.capture());
        assertThat(qualification.getValue().qualificationId()).isNull();
        assertThat(qualification.getValue().requestQualificationId()).isEqualTo(qualificationId);
        assertThat(qualification.getValue().authoritativeUserText())
                .isEqualTo("46, ship to 94107 in the US");
        assertThat(qualification.getValue().trustedReferenceProductText())
                .isEqualTo("Cool Running Shoes");

        ArgumentCaptor<SearchSimilarUserProductsCommand> search =
                ArgumentCaptor.forClass(SearchSimilarUserProductsCommand.class);
        verify(similaritySearches).search(eq(profile), search.capture());
        assertThat(search.getValue().qualificationId()).isEqualTo(qualificationId);
        assertThat(search.getValue().canonicalProductKey()).isEqualTo("canonical:owned-running-shoes");
        assertThat(search.getValue().query()).isEqualTo("running shoes");
        assertThat(search.getValue().language()).isEqualTo("cs-CZ");
        verifyNoInteractions(searches);
    }

    @Test
    void keepsTheConversationMerchantScopeOutOfTheModelSchemaAndInTheSearchCommand() {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        AgentProductSearchQualificationService qualifications =
                mock(AgentProductSearchQualificationService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserGroupedProductSearchResult result = mock(UserGroupedProductSearchResult.class);
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class))
                .thenReturn(new SearchCatalogAgentToolInput("trail shoes", 0, 10));
        when(profiles.profile(userId)).thenReturn(profile);
        when(qualifications.qualify(any(QualifyAgentProductSearchCommand.class)))
                .thenReturn(ready(
                        "recycled trail shoes",
                        availableOnly(),
                        Set.of(UserProductSearchQuestionTarget.SIZE),
                        Set.of(
                                UserProductSearchQuestionTarget.SIZE,
                                UserProductSearchQuestionTarget.TARGET_GENDER
                        )
                ));
        when(result.products()).thenReturn(List.of());
        when(searches.search(
                eq(profile),
                any(),
                any(CatalogDiscoveryFilters.class),
                any(),
                any()
        )).thenReturn(result);
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(
                json,
                profiles,
                results,
                qualifications,
                mock(AgentSimilaritySearchQualificationService.class),
                searches,
                mock(UserSimilarProductSearchService.class));

        tool.execute(context(userId)
                .withMerchantId(merchantId)
                .withBuyerIp("203.0.113.42")
                .withUserAgent("Meant Browser/1.0")
                .withLanguage("cs-CZ"), "{}");

        ArgumentCaptor<SearchUserProductsCommand> command =
                ArgumentCaptor.forClass(SearchUserProductsCommand.class);
        verify(searches).search(
                eq(profile),
                command.capture(),
                any(CatalogDiscoveryFilters.class),
                eq(Set.of(UserProductSearchQuestionTarget.SIZE)),
                eq(Set.of(
                        UserProductSearchQuestionTarget.SIZE,
                        UserProductSearchQuestionTarget.TARGET_GENDER
                ))
        );
        assertThat(command.getValue().merchantId()).isEqualTo(merchantId);
        assertThat(command.getValue().query()).isEqualTo("recycled trail shoes");
        assertThat(command.getValue().buyerIp()).isEqualTo("203.0.113.42");
        assertThat(command.getValue().userAgent()).isEqualTo("Meant Browser/1.0");
        assertThat(command.getValue().language()).isEqualTo("cs-CZ");
        assertThat(tool.descriptor().inputSchemaJson()).doesNotContain("merchantId");
    }

    @Test
    void keepsEightThousandCharacterInitialAndPendingTurnsAuthoritativeBehindBoundedRoutingQueries() {
        UUID userId = UUID.randomUUID();
        UUID pendingQualificationId = UUID.randomUUID();
        Instant pendingUpdatedAt = Instant.parse("2026-07-29T12:00:00Z");
        String initialTurn = boundedTurn("Find cool running shoes ");
        String pendingTurn = boundedTurn("46 ");
        String initialRoutingQuery = initialTurn.substring(0, 500);
        String pendingRoutingQuery = "Find cool running shoes " + "x".repeat(
                500 - "Find cool running shoes ".length()
        );
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        AgentProductSearchQualificationService qualifications =
                mock(AgentProductSearchQualificationService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        SearchCatalogAgentToolInput initialInput = new SearchCatalogAgentToolInput(
                initialRoutingQuery,
                null,
                null
        );
        SearchCatalogAgentToolInput pendingInput = new SearchCatalogAgentToolInput(
                pendingRoutingQuery,
                pendingQualificationId,
                pendingUpdatedAt,
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                null
        );
        when(json.readArguments("initial", SearchCatalogAgentToolInput.class)).thenReturn(initialInput);
        when(json.readArguments("pending", SearchCatalogAgentToolInput.class)).thenReturn(pendingInput);
        when(profiles.profile(userId)).thenReturn(profile);
        when(qualifications.qualify(any(QualifyAgentProductSearchCommand.class))).thenReturn(
                new AgentProductSearchQualificationResult(
                        UUID.randomUUID(),
                        "cool running shoes",
                        "What shoe size do you need?",
                        List.of(UserProductSearchQuestionTarget.SIZE),
                        null
                )
        );
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(
                json,
                profiles,
                results,
                qualifications,
                mock(AgentSimilaritySearchQualificationService.class),
                searches,
                mock(UserSimilarProductSearchService.class));

        tool.execute(context(userId, initialTurn), "initial");
        tool.execute(context(userId, pendingTurn), "pending");

        assertThat(initialInput.query()).hasSize(500);
        assertThat(pendingInput.query()).hasSize(500);
        ArgumentCaptor<QualifyAgentProductSearchCommand> command =
                ArgumentCaptor.forClass(QualifyAgentProductSearchCommand.class);
        verify(qualifications, org.mockito.Mockito.times(2)).qualify(command.capture());
        assertThat(command.getAllValues())
                .extracting(QualifyAgentProductSearchCommand::authoritativeUserText)
                .containsExactly(initialTurn, pendingTurn);
        assertThat(command.getAllValues())
                .extracting(QualifyAgentProductSearchCommand::qualificationId)
                .containsExactly(null, pendingQualificationId);
        assertThat(command.getAllValues().get(1).expectedQualificationUpdatedAt())
                .isEqualTo(pendingUpdatedAt);
        verifyNoInteractions(searches, results);
    }

    @Test
    void declaresServerControlledIdentityAndRejectsAnEmptyQueryBeforeDomainExecution() {
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        AgentProductSearchQualificationService qualifications =
                mock(AgentProductSearchQualificationService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class))
                .thenReturn(new SearchCatalogAgentToolInput(" ", null, null));
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(
                json,
                profiles,
                results,
                qualifications,
                mock(AgentSimilaritySearchQualificationService.class),
                searches,
                mock(UserSimilarProductSearchService.class));

        assertThat(tool.descriptor().name()).isEqualTo("search_catalog");
        assertThat(tool.descriptor().inputSchemaJson())
                .doesNotContain("userId")
                .contains("\"additionalProperties\":false");
        assertThatThrownBy(() -> tool.execute(context(), "{}"))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("query is required");
        verifyNoInteractions(profiles, results, qualifications, searches);
    }

    @Test
    void acceptsLegacyTypedArgumentsOnlyAfterServerQualificationButDoesNotExposeThemToTheOuterModel() {
        UUID userId = UUID.randomUUID();
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        AgentProductSearchQualificationService qualifications =
                mock(AgentProductSearchQualificationService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserGroupedProductSearchResult result = mock(UserGroupedProductSearchResult.class);
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class)).thenReturn(
                new SearchCatalogAgentToolInput(
                        "black football boots",
                        new SearchCatalogAgentToolInput.Location("US", "NY", "10001"),
                        List.of(new SearchCatalogAgentToolInput.Origin("CA")),
                        new SearchCatalogAgentToolInput.Price(
                                new BigDecimal("50.00"), new BigDecimal("180.25")),
                        List.of("new"),
                        List.of(
                                new SearchCatalogAgentToolInput.Attribute("Color", List.of("Black")),
                                new SearchCatalogAgentToolInput.Attribute("Size", List.of("10")),
                                new SearchCatalogAgentToolInput.Attribute("Target gender", List.of("Unisex"))
                        ),
                        null,
                        List.of(),
                        0,
                        10
                )
        );
        when(profiles.profile(userId)).thenReturn(profile);
        CatalogDiscoveryFilters authorized = new CatalogDiscoveryFilters(
                true,
                List.of(CatalogDiscoveryCondition.NEW),
                new com.meant.api.module.catalog.service.dto.CatalogDiscoveryLocation("US", "NY", "10001"),
                List.of(new com.meant.api.module.catalog.service.dto.CatalogDiscoveryLocation("CA", null, null)),
                new com.meant.api.module.catalog.service.dto.CatalogDiscoveryPrice(5_000L, 18_025L),
                List.of(),
                List.of(),
                List.of(
                        new com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter(
                                CatalogDiscoveryAttributeName.COLOR, List.of("Black")),
                        new com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter(
                                CatalogDiscoveryAttributeName.SIZE, List.of("10")),
                        new com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter(
                                CatalogDiscoveryAttributeName.TARGET_GENDER, List.of("Unisex"))
                ),
                null,
                List.of()
        );
        when(qualifications.qualify(any(QualifyAgentProductSearchCommand.class)))
                .thenReturn(ready("black football boots", authorized));
        when(result.products()).thenReturn(List.of());
        when(searches.search(
                eq(profile),
                any(),
                any(CatalogDiscoveryFilters.class),
                any(),
                any()
        )).thenReturn(result);
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(
                json,
                profiles,
                results,
                qualifications,
                mock(AgentSimilaritySearchQualificationService.class),
                searches,
                mock(UserSimilarProductSearchService.class));

        tool.execute(context(userId), "{}");

        ArgumentCaptor<CatalogDiscoveryFilters> filters =
                ArgumentCaptor.forClass(CatalogDiscoveryFilters.class);
        verify(searches).search(
                eq(profile),
                any(SearchUserProductsCommand.class),
                filters.capture(),
                any(),
                any()
        );
        assertThat(filters.getValue()).satisfies(value -> {
            assertThat(value.available()).isTrue();
            assertThat(value.conditions()).containsExactly(CatalogDiscoveryCondition.NEW);
            assertThat(value.shipsTo().country()).isEqualTo("US");
            assertThat(value.shipsTo().region()).isEqualTo("NY");
            assertThat(value.shipsTo().postalCode()).isEqualTo("10001");
            assertThat(value.shipsFrom()).singleElement()
                    .satisfies(origin -> assertThat(origin.country()).isEqualTo("CA"));
            assertThat(value.price().min()).isEqualTo(5_000L);
            assertThat(value.price().max()).isEqualTo(18_025L);
            assertThat(value.attributes()).extracting(attribute -> attribute.name())
                    .containsExactly(
                            CatalogDiscoveryAttributeName.COLOR,
                            CatalogDiscoveryAttributeName.SIZE,
                            CatalogDiscoveryAttributeName.TARGET_GENDER
                    );
            assertThat(value.rating()).isNull();
            assertThat(value.priceTiers()).isEmpty();
            assertThat(value.shopIds()).isEmpty();
            assertThat(value.categoryIds()).isEmpty();
        });
        assertThat(tool.descriptor().inputSchemaJson())
                .doesNotContain(
                        "\"shipsTo\"",
                        "\"shipsFrom\"",
                        "\"attributes\"",
                        "\"rating\"",
                        "\"priceTiers\"",
                        "\"shops\"",
                        "\"categories\""
                );
    }

    @Test
    void preservesExplicitRatingAndPriceTierWhenTheSearchIsEmpty() {
        UUID userId = UUID.randomUUID();
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        AgentProductSearchQualificationService qualifications =
                mock(AgentProductSearchQualificationService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        SearchCatalogAgentToolInput input = new SearchCatalogAgentToolInput(
                "size 10 football boots under $150",
                new SearchCatalogAgentToolInput.Location("US", null, null),
                List.of(),
                new SearchCatalogAgentToolInput.Price(null, new BigDecimal("150")),
                List.of("new"),
                List.of(new SearchCatalogAgentToolInput.Attribute("Size", List.of("10"))),
                new SearchCatalogAgentToolInput.Rating(new BigDecimal("4.8"), 100L),
                List.of("low"),
                0,
                10
        );
        UserGroupedProductSearchResult empty = mock(UserGroupedProductSearchResult.class);
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class)).thenReturn(input);
        when(profiles.profile(userId)).thenReturn(profile);
        CatalogDiscoveryFilters authorized = new CatalogDiscoveryFilters(
                true,
                List.of(CatalogDiscoveryCondition.NEW),
                new com.meant.api.module.catalog.service.dto.CatalogDiscoveryLocation("US", null, null),
                List.of(),
                new com.meant.api.module.catalog.service.dto.CatalogDiscoveryPrice(null, 15_000L),
                List.of(),
                List.of(),
                List.of(new com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter(
                        CatalogDiscoveryAttributeName.SIZE, List.of("10"))),
                new com.meant.api.module.catalog.service.dto.CatalogDiscoveryRating(new BigDecimal("4.8"), 100L),
                List.of(CatalogDiscoveryPriceTier.LOW)
        );
        when(qualifications.qualify(any(QualifyAgentProductSearchCommand.class)))
                .thenReturn(ready("football boots", authorized));
        when(empty.products()).thenReturn(List.of());
        when(searches.search(
                eq(profile),
                any(),
                any(CatalogDiscoveryFilters.class),
                any(),
                any()
        ))
                .thenReturn(empty);
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(
                json,
                profiles,
                results,
                qualifications,
                mock(AgentSimilaritySearchQualificationService.class),
                searches,
                mock(UserSimilarProductSearchService.class));

        var execution = tool.execute(context(userId), "{}");

        ArgumentCaptor<CatalogDiscoveryFilters> filters =
                ArgumentCaptor.forClass(CatalogDiscoveryFilters.class);
        verify(searches).search(
                eq(profile),
                any(SearchUserProductsCommand.class),
                filters.capture(),
                any(),
                any()
        );
        assertThat(filters.getValue().rating()).isEqualTo(authorized.rating());
        assertThat(filters.getValue().priceTiers()).containsExactly(CatalogDiscoveryPriceTier.LOW);
        assertThat(execution.safeSummary()).isEqualTo("Found 0 grounded product option(s).");
    }

    @Test
    void blocksCatalogExecutionWhenServerQualificationFindsCriticalBootGaps() {
        UUID userId = UUID.randomUUID();
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        AgentProductSearchQualificationService qualifications =
                mock(AgentProductSearchQualificationService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UUID qualificationId = UUID.randomUUID();
        Instant qualificationUpdatedAt = Instant.parse("2026-07-29T12:00:00Z");
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class))
                .thenReturn(new SearchCatalogAgentToolInput(
                        "football boots",
                        qualificationId,
                        qualificationUpdatedAt,
                        null,
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        0,
                        10
                ));
        when(profiles.profile(userId)).thenReturn(profile);
        when(qualifications.qualify(any(QualifyAgentProductSearchCommand.class))).thenReturn(
                new AgentProductSearchQualificationResult(
                        UUID.randomUUID(),
                        "football boots",
                        "What boot size do you need, and what country should they ship to?",
                        List.of(UserProductSearchQuestionTarget.SIZE, UserProductSearchQuestionTarget.SHIPS_TO),
                        null
                )
        );
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(
                json,
                profiles,
                results,
                qualifications,
                mock(AgentSimilaritySearchQualificationService.class),
                searches,
                mock(UserSimilarProductSearchService.class));

        var execution = tool.execute(context(userId), "{}");

        assertThat(execution.safeSummary()).contains("What boot size", "ship to");
        assertThat(execution.waitingForUserMessage()).isEqualTo(
                "What boot size do you need, and what country should they ship to?");
        ArgumentCaptor<QualifyAgentProductSearchCommand> command =
                ArgumentCaptor.forClass(QualifyAgentProductSearchCommand.class);
        verify(qualifications).qualify(command.capture());
        assertThat(command.getValue().qualificationId()).isEqualTo(qualificationId);
        assertThat(command.getValue().expectedQualificationUpdatedAt()).isEqualTo(qualificationUpdatedAt);
        assertThat(tool.descriptor().inputSchemaJson())
                .contains("\"qualificationId\"", "\"qualificationUpdatedAt\"");
        verifyNoInteractions(searches, results);
    }

    @Test
    void passesQualifiedExtensionConstraintsToMerchantScopedSearch() {
        UUID userId = UUID.randomUUID();
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        AgentProductSearchQualificationService qualifications =
                mock(AgentProductSearchQualificationService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        SearchCatalogAgentToolInput input = new SearchCatalogAgentToolInput(
                "size 10 football boots",
                null,
                List.of(),
                null,
                List.of(),
                List.of(new SearchCatalogAgentToolInput.Attribute("Size", List.of("10"))),
                null,
                List.of(),
                0,
                10
        );
        CatalogDiscoveryFilters authorized = new CatalogDiscoveryFilters(
                true,
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(new com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter(
                        CatalogDiscoveryAttributeName.SIZE, List.of("10"))),
                null,
                List.of()
        );
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class)).thenReturn(input);
        when(profiles.profile(userId)).thenReturn(profile);
        when(qualifications.qualify(any(QualifyAgentProductSearchCommand.class)))
                .thenReturn(ready("football boots", authorized));
        UserGroupedProductSearchResult result = mock(UserGroupedProductSearchResult.class);
        when(result.products()).thenReturn(List.of());
        when(searches.search(eq(profile), any(), eq(authorized), any(), any())).thenReturn(result);
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(
                json,
                profiles,
                results,
                qualifications,
                mock(AgentSimilaritySearchQualificationService.class),
                searches,
                mock(UserSimilarProductSearchService.class));

        tool.execute(context(userId).withMerchantId(UUID.randomUUID()), "{}");

        verify(searches).search(
                eq(profile),
                any(SearchUserProductsCommand.class),
                eq(authorized),
                any(),
                any()
        );
    }

    private AgentProductSearchQualificationResult ready(
            String query,
            CatalogDiscoveryFilters filters
    ) {
        return new AgentProductSearchQualificationResult(
                UUID.randomUUID(), query, "Ready", List.of(), filters);
    }

    private AgentProductSearchQualificationResult ready(
            String query,
            CatalogDiscoveryFilters filters,
            Set<UserProductSearchQuestionTarget> explicitAnyTargets,
            Set<UserProductSearchQuestionTarget> profileSuppressionTargets
    ) {
        return new AgentProductSearchQualificationResult(
                UUID.randomUUID(),
                query,
                "Ready",
                List.of(),
                filters,
                explicitAnyTargets,
                profileSuppressionTargets
        );
    }

    private CatalogDiscoveryFilters availableOnly() {
        return new CatalogDiscoveryFilters(
                true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of());
    }

    private AgentToolExecutionContext context() {
        return context(UUID.randomUUID());
    }

    private AgentToolExecutionContext context(UUID userId) {
        return context(userId, "find shoes");
    }

    private AgentToolExecutionContext context(UUID userId, String turn) {
        return new AgentToolExecutionContext(
                userId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), turn);
    }

    private String boundedTurn(String prefix) {
        return prefix + "x".repeat(8_000 - prefix.length());
    }
}
