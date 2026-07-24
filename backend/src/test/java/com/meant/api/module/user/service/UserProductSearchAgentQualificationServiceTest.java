package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeName;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationModelResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserProductSearchAgentQualificationServiceTest {

    @Test
    void loadsDurablePreferencesAndReturnsOnlyServerMappedHardFilters() {
        UserSettingsService settingsService = mock(UserSettingsService.class);
        UserProductSearchPreferenceService preferenceService = mock(UserProductSearchPreferenceService.class);
        UserProductSearchQualificationModelService modelService =
                mock(UserProductSearchQualificationModelService.class);
        UserProductSearchQualificationPlanMapper mapper = mock(UserProductSearchQualificationPlanMapper.class);
        UserProductSearchQualificationPlan plan = mock(UserProductSearchQualificationPlan.class);
        UserSettingsResult settings = mock(UserSettingsResult.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                UUID.randomUUID(), "shopper@example.test", "Shopper", null);
        List<UserProductSearchPreferenceResult> durable = List.of(new UserProductSearchPreferenceResult(
                "football-boots", UserProductSearchAttributeName.SIZE, List.of("10")));
        CatalogDiscoveryFilters filters = new CatalogDiscoveryFilters(
                true,
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(new CatalogDiscoveryAttributeFilter(
                        CatalogDiscoveryAttributeName.SIZE, List.of("10"))),
                null,
                List.of()
        );
        when(settingsService.get(profile)).thenReturn(settings);
        when(preferenceService.list(profile.id())).thenReturn(durable);
        when(modelService.generate(any())).thenReturn(
                new UserProductSearchQualificationModelResult(plan, "model", "prompt"));
        when(plan.missingFilters()).thenReturn(List.of());
        when(plan.missingTargets()).thenReturn(List.of());
        when(plan.effectiveQuery()).thenReturn("football boots");
        when(mapper.map(plan)).thenReturn(filters);
        var service = new UserProductSearchAgentQualificationService(
                settingsService, preferenceService, modelService, mapper);

        var result = service.qualify(
                profile,
                "I need boots in my usual size",
                "football boots"
        );

        ArgumentCaptor<GenerateUserProductSearchQualificationQuery> query =
                ArgumentCaptor.forClass(GenerateUserProductSearchQualificationQuery.class);
        verify(modelService).generate(query.capture());
        assertThat(query.getValue().settings()).isSameAs(settings);
        assertThat(query.getValue().durablePreferences()).isEqualTo(durable);
        assertThat(query.getValue().originalQuery()).isEqualTo("I need boots in my usual size");
        assertThat(query.getValue().message()).isEqualTo("I need boots in my usual size");
        assertThat(query.getValue().catalogQueryHint()).isEqualTo("football boots");
        assertThat(result.ready()).isTrue();
        assertThat(result.filters()).isSameAs(filters);
    }
}
