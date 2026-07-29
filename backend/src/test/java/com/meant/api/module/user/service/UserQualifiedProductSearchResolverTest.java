package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationSnapshot;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class UserQualifiedProductSearchResolverTest {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID QUALIFICATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CONVERSATION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void refusesToExecuteAReadyPlanFromAnOutdatedSchema() {
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper planMapper = mock(UserProductSearchQualificationPlanMapper.class);
        UserProductSearchQualificationPlan legacyPlan = plan(1);
        when(persistenceService.getReady(query())).thenReturn(snapshot(legacyPlan));
        UserQualifiedProductSearchResolver resolver = resolver(persistenceService, planMapper);

        assertThatThrownBy(() -> resolver.resolve(USER_ID, QUALIFICATION_ID))
                .isInstanceOfSatisfying(UserException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception).hasMessageContaining("outdated plan");
                });
        verifyNoInteractions(planMapper);
    }

    @Test
    void mapsAnUnexpiredReadyPlanFromTheCurrentSchema() {
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper planMapper = mock(UserProductSearchQualificationPlanMapper.class);
        UserProductSearchQualificationPlan currentPlan = plan(
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION);
        CatalogDiscoveryFilters expectedFilters = new CatalogDiscoveryFilters(
                true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of());
        when(persistenceService.getReady(query())).thenReturn(snapshot(currentPlan));
        when(planMapper.map(currentPlan)).thenReturn(expectedFilters);
        UserQualifiedProductSearchResolver resolver = resolver(persistenceService, planMapper);

        var resolved = resolver.resolve(USER_ID, QUALIFICATION_ID);

        assertThat(resolved.qualificationId()).isEqualTo(QUALIFICATION_ID);
        assertThat(resolved.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(resolved.effectiveQuery()).isEqualTo("running shoes");
        assertThat(resolved.filters()).isSameAs(expectedFilters);
        verify(planMapper).map(currentPlan);
    }

    @Test
    void usesTheSanitizedPlanQueryInsteadOfTheOriginalQualificationRequest() {
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper planMapper = mock(UserProductSearchQualificationPlanMapper.class);
        UserProductSearchQualificationPlan sanitizedPlan = plan(
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION,
                "black jacket"
        );
        CatalogDiscoveryFilters expectedFilters = new CatalogDiscoveryFilters(
                true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of());
        when(persistenceService.getReady(query()))
                .thenReturn(snapshot("men's black jacket", sanitizedPlan));
        when(planMapper.map(sanitizedPlan)).thenReturn(expectedFilters);
        UserQualifiedProductSearchResolver resolver = resolver(persistenceService, planMapper);

        var resolved = resolver.resolve(USER_ID, QUALIFICATION_ID);

        assertThat(resolved.effectiveQuery()).isEqualTo("black jacket");
    }

    private UserQualifiedProductSearchResolver resolver(
            UserProductSearchQualificationPersistenceService persistenceService,
            UserProductSearchQualificationPlanMapper planMapper
    ) {
        return new UserQualifiedProductSearchResolver(persistenceService, planMapper, searchProperties());
    }

    private GetUserProductSearchQualificationQuery query() {
        return new GetUserProductSearchQualificationQuery(USER_ID, QUALIFICATION_ID);
    }

    private UserProductSearchQualificationSnapshot snapshot(UserProductSearchQualificationPlan plan) {
        return snapshot("running shoes", plan);
    }

    private UserProductSearchQualificationSnapshot snapshot(
            String originalQuery,
            UserProductSearchQualificationPlan plan
    ) {
        Instant now = Instant.now();
        return new UserProductSearchQualificationSnapshot(
                QUALIFICATION_ID,
                USER_ID,
                CONVERSATION_ID,
                null,
                originalQuery,
                UserProductSearchQualificationStatus.READY,
                plan,
                "qualification-model",
                "qualification-v2",
                now,
                now
        );
    }

    private UserProductSearchQualificationPlan plan(int schemaVersion) {
        return plan(schemaVersion, "running shoes");
    }

    private UserProductSearchQualificationPlan plan(int schemaVersion, String effectiveQuery) {
        return new UserProductSearchQualificationPlan(
                schemaVersion,
                effectiveQuery,
                "Ready to search.",
                List.of(),
                List.of(),
                new UserProductSearchQualificationPlan.AvailableFilter(
                        UserProductSearchFilterState.VALUE,
                        true,
                        UserProductSearchQualificationPlan.Provenance.system("sale-ready products only")
                ),
                new UserProductSearchQualificationPlan.ConditionFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null),
                new UserProductSearchQualificationPlan.LocationsFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null, null),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.AttributesFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null, null),
                new UserProductSearchQualificationPlan.PriceTierFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                List.of()
        );
    }

    private UserProductSearchProperties searchProperties() {
        return new UserProductSearchProperties(
                "search-v1",
                "qualification-v2",
                "explanation-v1",
                4096,
                Duration.ofHours(1),
                Duration.ofMinutes(30),
                Duration.ofMinutes(30),
                100,
                Duration.ofSeconds(30),
                10,
                5,
                5,
                Duration.ofDays(1),
                Duration.ofDays(7),
                10,
                2,
                80
        );
    }
}
