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
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.SearchCatalogAgentToolInput;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeName;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryCondition;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryPriceTier;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import com.meant.api.module.user.service.UserProductSearchAgentQualificationService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserProductSearchAgentQualificationResult;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SearchCatalogAgentToolTest {

    @Test
    void keepsTheConversationMerchantScopeOutOfTheModelSchemaAndInTheSearchCommand() {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        UserProductSearchAgentQualificationService qualifications =
                mock(UserProductSearchAgentQualificationService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserGroupedProductSearchResult result = mock(UserGroupedProductSearchResult.class);
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class))
                .thenReturn(new SearchCatalogAgentToolInput("trail shoes", 0, 10));
        when(profiles.profile(userId)).thenReturn(profile);
        when(qualifications.qualify(
                eq(profile), any(UUID.class), nullable(UUID.class), nullable(UUID.class), eq("find shoes")))
                .thenReturn(ready("find shoes", availableOnly()));
        when(result.products()).thenReturn(List.of());
        when(searches.search(eq(profile), any(), any(CatalogDiscoveryFilters.class))).thenReturn(result);
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(
                json, profiles, results, qualifications, searches);

        tool.execute(context(userId).withMerchantId(merchantId), "{}");

        ArgumentCaptor<SearchUserProductsCommand> command =
                ArgumentCaptor.forClass(SearchUserProductsCommand.class);
        verify(searches).search(eq(profile), command.capture(), any(CatalogDiscoveryFilters.class));
        assertThat(command.getValue().merchantId()).isEqualTo(merchantId);
        assertThat(command.getValue().query()).isEqualTo("find shoes");
        assertThat(tool.descriptor().inputSchemaJson()).doesNotContain("merchantId");
    }

    @Test
    void declaresServerControlledIdentityAndRejectsAnEmptyQueryBeforeDomainExecution() {
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        UserProductSearchAgentQualificationService qualifications =
                mock(UserProductSearchAgentQualificationService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class))
                .thenReturn(new SearchCatalogAgentToolInput(" ", null, null));
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(
                json, profiles, results, qualifications, searches);

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
    void mapsDocumentedUcpAndShopifyExtensionConstraintsWithoutExposingTrustedIds() {
        UUID userId = UUID.randomUUID();
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        UserProductSearchAgentQualificationService qualifications =
                mock(UserProductSearchAgentQualificationService.class);
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
        when(qualifications.qualify(
                eq(profile), any(UUID.class), nullable(UUID.class), nullable(UUID.class), eq("find shoes")))
                .thenReturn(ready("black football boots", authorized));
        when(result.products()).thenReturn(List.of());
        when(searches.search(eq(profile), any(), any(CatalogDiscoveryFilters.class))).thenReturn(result);
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(
                json, profiles, results, qualifications, searches);

        tool.execute(context(userId), "{}");

        ArgumentCaptor<CatalogDiscoveryFilters> filters =
                ArgumentCaptor.forClass(CatalogDiscoveryFilters.class);
        verify(searches).search(eq(profile), any(SearchUserProductsCommand.class), filters.capture());
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
                .contains("\"shipsTo\"", "\"shipsFrom\"", "\"attributes\"", "\"rating\"", "\"priceTiers\"")
                .doesNotContain("\"shops\"", "\"categories\"");
    }

    @Test
    void preservesExplicitRatingAndPriceTierWhenTheSearchIsEmpty() {
        UUID userId = UUID.randomUUID();
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        UserProductSearchAgentQualificationService qualifications =
                mock(UserProductSearchAgentQualificationService.class);
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
        when(qualifications.qualify(
                eq(profile), any(UUID.class), nullable(UUID.class), nullable(UUID.class), eq("find shoes")))
                .thenReturn(ready("football boots", authorized));
        when(empty.products()).thenReturn(List.of());
        when(searches.search(eq(profile), any(), any(CatalogDiscoveryFilters.class)))
                .thenReturn(empty);
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(
                json, profiles, results, qualifications, searches);

        var execution = tool.execute(context(userId), "{}");

        ArgumentCaptor<CatalogDiscoveryFilters> filters =
                ArgumentCaptor.forClass(CatalogDiscoveryFilters.class);
        verify(searches).search(eq(profile), any(SearchUserProductsCommand.class), filters.capture());
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
        UserProductSearchAgentQualificationService qualifications =
                mock(UserProductSearchAgentQualificationService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UUID qualificationId = UUID.randomUUID();
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class))
                .thenReturn(new SearchCatalogAgentToolInput("football boots", qualificationId, 0, 10));
        when(profiles.profile(userId)).thenReturn(profile);
        when(qualifications.qualify(
                eq(profile), any(UUID.class), nullable(UUID.class), eq(qualificationId), eq("find shoes"))).thenReturn(
                new UserProductSearchAgentQualificationResult(
                        UUID.randomUUID(),
                        "football boots",
                        "What boot size do you need, and what country or postal code should they ship to?",
                        List.of(UserProductSearchQuestionTarget.SIZE, UserProductSearchQuestionTarget.SHIPS_TO),
                        null
                )
        );
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(
                json, profiles, results, qualifications, searches);

        var execution = tool.execute(context(userId), "{}");

        assertThat(execution.safeSummary()).contains("What boot size", "ship to");
        assertThat(tool.descriptor().inputSchemaJson()).contains("\"qualificationId\"");
        verifyNoInteractions(searches, results);
    }

    @Test
    void failsClosedWhenMerchantScopedSearchCannotEnforceAQualifiedExtensionConstraint() {
        UUID userId = UUID.randomUUID();
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        UserProductSearchAgentQualificationService qualifications =
                mock(UserProductSearchAgentQualificationService.class);
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
        when(qualifications.qualify(
                eq(profile), any(UUID.class), nullable(UUID.class), nullable(UUID.class), eq("find shoes")))
                .thenReturn(ready("football boots", authorized));
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(
                json, profiles, results, qualifications, searches);

        assertThatThrownBy(() -> tool.execute(
                context(userId).withMerchantId(UUID.randomUUID()), "{}"))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("cannot enforce");
        verifyNoInteractions(searches, results);
    }

    private UserProductSearchAgentQualificationResult ready(
            String query,
            CatalogDiscoveryFilters filters
    ) {
        return new UserProductSearchAgentQualificationResult(
                UUID.randomUUID(), query, "Ready", List.of(), filters);
    }

    private CatalogDiscoveryFilters availableOnly() {
        return new CatalogDiscoveryFilters(
                true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of());
    }

    private AgentToolExecutionContext context() {
        return context(UUID.randomUUID());
    }

    private AgentToolExecutionContext context(UUID userId) {
        return new AgentToolExecutionContext(
                userId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "find shoes");
    }
}
