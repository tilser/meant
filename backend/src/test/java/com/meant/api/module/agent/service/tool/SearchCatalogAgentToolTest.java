package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.AgentProductSearchQualificationService;
import com.meant.api.module.agent.service.AgentSimilaritySearchQualificationService;
import com.meant.api.module.agent.service.dto.AgentAppliedSearchFilter;
import com.meant.api.module.agent.service.dto.AgentProductListResult;
import com.meant.api.module.agent.service.dto.AgentProductSearchQualificationResult;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.SearchCatalogAgentToolInput;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import com.meant.api.module.user.service.UserProductSearchCatalogInputBuilder;
import com.meant.api.module.user.service.UserSettingsService;
import com.meant.api.module.user.service.UserSimilarProductSearchService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SearchCatalogAgentToolTest {

    @Test
    void modelSchemaUsesTypedOptionalFiltersWithoutQualificationTokens() {
        SearchCatalogAgentTool tool = tool(
                mock(AgentJsonSupport.class),
                mock(AgentContextProfileService.class),
                mock(AgentProductSearchQualificationService.class),
                mock(UserGroupedProductSearchService.class)
        );

        assertThat(tool.descriptor().inputSchemaJson())
                .contains("shipsTo", "attributes", "priceTiers", "minAmount", "maxAmount")
                .doesNotContain("minUsd", "maxUsd")
                .doesNotContain("qualificationId", "qualificationUpdatedAt");
        assertThat(tool.descriptor().description())
                .contains("appliedFilters", "unsetFilters")
                .contains("missing filters never prevent a search");
    }

    @Test
    void convertsRequestedPriceUsingTheExplicitRequestCurrencyInsteadOfTheAccountCurrency() {
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductSearchQualificationService qualifications =
                mock(AgentProductSearchQualificationService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        UUID userId = UUID.randomUUID();
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        SearchCatalogAgentToolInput input = new SearchCatalogAgentToolInput(
                "tichý kávovar",
                null,
                List.of(),
                new SearchCatalogAgentToolInput.Price(null, new BigDecimal("5000")),
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                null
        );

        when(json.readArguments("{}", SearchCatalogAgentToolInput.class)).thenReturn(input);
        when(json.write(any())).thenReturn("{}");
        when(profiles.profile(userId)).thenReturn(profile);
        when(qualifications.qualify(any())).thenReturn(new AgentProductSearchQualificationResult(
                UUID.randomUUID(), "tichý kávovar", null, Map.of(), List.of(), Set.of(), Set.of()));
        when(searches.search(eq(profile), any(), any(), eq(Set.of()), eq(Set.of())))
                .thenReturn(emptyResult("tichý kávovar"));

        tool(json, profiles, qualifications, searches, "USD").execute(
                new AgentToolExecutionContext(
                        userId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Najdi mi tichý kávovar do 5 000 Kč"
                ),
                "{}"
        );

        ArgumentCaptor<CatalogDiscoveryFilters> filters = ArgumentCaptor.forClass(CatalogDiscoveryFilters.class);
        verify(searches).search(eq(profile), any(), filters.capture(), eq(Set.of()), eq(Set.of()));
        assertThat(filters.getValue().price().max()).isEqualTo(500_000L);
        assertThat(filters.getValue().price().currency()).isEqualTo("CZK");
    }

    @Test
    void missingAdvisoryDimensionsStillReturnAProductResultEnvelope() {
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductSearchQualificationService qualifications =
                mock(AgentProductSearchQualificationService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        CatalogDiscoveryFilters partialFilters = new CatalogDiscoveryFilters(
                true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of());

        when(json.readArguments("{}", SearchCatalogAgentToolInput.class))
                .thenReturn(new SearchCatalogAgentToolInput("trail shoes", null, null));
        when(json.write(any())).thenReturn("{}");
        when(profiles.profile(userId)).thenReturn(profile);
        when(qualifications.qualify(any())).thenReturn(new AgentProductSearchQualificationResult(
                UUID.randomUUID(),
                "trail shoes",
                partialFilters,
                Map.of("available", new AgentAppliedSearchFilter(
                        List.of("true"), UserProductSearchDecisionSource.PROFILE)),
                List.of(UserProductSearchQuestionTarget.SIZE),
                Set.of(),
                Set.of()
        ));
        when(searches.search(eq(profile), any(), eq(partialFilters), eq(Set.of()), eq(Set.of())))
                .thenReturn(emptyResult("trail shoes"));

        var result = tool(json, profiles, qualifications, searches).execute(
                new AgentToolExecutionContext(
                        userId, conversationId, UUID.randomUUID(), messageId, "Find trail shoes"),
                "{}"
        );

        assertThat(result.waitingForUserMessage()).isNull();
        assertThat(result.safeSummary()).isEqualTo("Found 0 grounded product option(s).");
        ArgumentCaptor<Object> output = ArgumentCaptor.forClass(Object.class);
        verify(json).write(output.capture());
        assertThat(output.getValue()).isInstanceOfSatisfying(AgentProductListResult.class, payload -> {
            assertThat(payload.resultCount()).isZero();
            assertThat(payload.unsetFilters()).containsExactly(UserProductSearchQuestionTarget.SIZE);
            assertThat(payload.appliedFilters()).containsKey("available");
        });
    }

    private SearchCatalogAgentTool tool(
            AgentJsonSupport json,
            AgentContextProfileService profiles,
            AgentProductSearchQualificationService qualifications,
            UserGroupedProductSearchService searches
    ) {
        return tool(json, profiles, qualifications, searches, "USD");
    }

    private SearchCatalogAgentTool tool(
            AgentJsonSupport json,
            AgentContextProfileService profiles,
            AgentProductSearchQualificationService qualifications,
            UserGroupedProductSearchService searches,
            String currency
    ) {
        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.get(any())).thenReturn(settings(currency));
        return new SearchCatalogAgentTool(
                json,
                profiles,
                mock(AgentProductReadResultService.class),
                qualifications,
                mock(AgentSimilaritySearchQualificationService.class),
                searches,
                mock(UserSimilarProductSearchService.class),
                settings,
                new UserProductSearchCatalogInputBuilder(null)
        );
    }

    private UserSettingsResult settings(String currency) {
        Instant now = Instant.parse("2026-06-17T10:00:00Z");
        return new UserSettingsResult(
                120, currency, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), now, now);
    }

    private UserGroupedProductSearchResult emptyResult(String query) {
        return new UserGroupedProductSearchResult(
                query, query, "profile", false, 0, 20, null, false, false,
                List.of(), 0, false, List.of());
    }
}
