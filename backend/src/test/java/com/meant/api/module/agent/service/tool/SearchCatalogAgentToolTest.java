package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
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
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
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
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserGroupedProductSearchResult result = mock(UserGroupedProductSearchResult.class);
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class))
                .thenReturn(new SearchCatalogAgentToolInput("trail shoes", 0, 10));
        when(profiles.profile(userId)).thenReturn(profile);
        when(result.products()).thenReturn(List.of());
        when(searches.search(eq(profile), any())).thenReturn(result);
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(json, profiles, results, searches);

        tool.execute(context(userId).withMerchantId(merchantId), "{}");

        ArgumentCaptor<SearchUserProductsCommand> command =
                ArgumentCaptor.forClass(SearchUserProductsCommand.class);
        verify(searches).search(eq(profile), command.capture());
        assertThat(command.getValue().merchantId()).isEqualTo(merchantId);
        assertThat(tool.descriptor().inputSchemaJson()).doesNotContain("merchantId");
    }

    @Test
    void declaresServerControlledIdentityAndRejectsAnEmptyQueryBeforeDomainExecution() {
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class))
                .thenReturn(new SearchCatalogAgentToolInput(" ", null, null));
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(json, profiles, results, searches);

        assertThat(tool.descriptor().name()).isEqualTo("search_catalog");
        assertThat(tool.descriptor().inputSchemaJson())
                .doesNotContain("userId")
                .contains("\"additionalProperties\":false");
        assertThatThrownBy(() -> tool.execute(context(), "{}"))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("query is required");
        verifyNoInteractions(profiles, results, searches);
    }

    @Test
    void mapsDocumentedUcpAndShopifyExtensionConstraintsWithoutExposingTrustedIds() {
        UUID userId = UUID.randomUUID();
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
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
        when(result.products()).thenReturn(List.of());
        when(searches.search(eq(profile), any(), any(CatalogDiscoveryFilters.class))).thenReturn(result);
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(json, profiles, results, searches);

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
    void retriesEmptyFirstPageByRelaxingOnlyRatingAndPriceTier() {
        UUID userId = UUID.randomUUID();
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
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
        UserGroupedProductSearchResult relaxedResult = mock(UserGroupedProductSearchResult.class);
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class)).thenReturn(input);
        when(profiles.profile(userId)).thenReturn(profile);
        when(empty.products()).thenReturn(List.of());
        when(relaxedResult.products()).thenReturn(List.of());
        when(searches.search(eq(profile), any(), any(CatalogDiscoveryFilters.class)))
                .thenReturn(empty, relaxedResult);
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(json, profiles, results, searches);

        var execution = tool.execute(context(userId), "{}");

        ArgumentCaptor<CatalogDiscoveryFilters> filters =
                ArgumentCaptor.forClass(CatalogDiscoveryFilters.class);
        verify(searches, times(2)).search(eq(profile), any(SearchUserProductsCommand.class), filters.capture());
        CatalogDiscoveryFilters original = filters.getAllValues().get(0);
        CatalogDiscoveryFilters relaxed = filters.getAllValues().get(1);
        assertThat(original.rating()).isNotNull();
        assertThat(original.priceTiers()).containsExactly(CatalogDiscoveryPriceTier.LOW);
        assertThat(relaxed.rating()).isNull();
        assertThat(relaxed.priceTiers()).isEmpty();
        assertThat(relaxed.shipsTo()).isEqualTo(original.shipsTo());
        assertThat(relaxed.price()).isEqualTo(original.price());
        assertThat(relaxed.conditions()).isEqualTo(original.conditions());
        assertThat(relaxed.attributes()).isEqualTo(original.attributes());
        assertThat(execution.safeSummary()).contains(
                "rating or relative price-tier thresholds",
                "Destination, price, condition, and product attributes were preserved"
        );
    }

    private AgentToolExecutionContext context() {
        return context(UUID.randomUUID());
    }

    private AgentToolExecutionContext context(UUID userId) {
        return new AgentToolExecutionContext(
                userId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "find shoes");
    }
}
