package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeName;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationSnapshot;
import com.meant.api.module.user.service.query.FindPendingUserProductSearchQualificationQuery;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserProductSearchAgentQualificationServiceTest {

    @Test
    void resumesTheLatestPendingConversationQualificationAndUsesItsValidatedPlan() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper mapper = mock(UserProductSearchQualificationPlanMapper.class);
        UserProductSearchQualificationPlan pendingPlan = mock(UserProductSearchQualificationPlan.class);
        UserProductSearchQualificationPlan readyPlan = mock(UserProductSearchQualificationPlan.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserProductSearchQualificationSnapshot pending = snapshot(
                qualificationId,
                userId,
                conversationId,
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                pendingPlan
        );
        UserProductSearchQualificationSnapshot ready = snapshot(
                qualificationId,
                userId,
                conversationId,
                UserProductSearchQualificationStatus.READY,
                readyPlan
        );
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
        when(persistenceService.findLatestPending(any())).thenReturn(Optional.of(pending));
        when(qualificationService.qualify(any(), any())).thenReturn(new UserProductSearchQualificationResult(
                qualificationId,
                UserProductSearchQualificationStatus.READY,
                "Ready",
                List.of(),
                List.of(),
                "football boots"
        ));
        when(persistenceService.find(any(GetUserProductSearchQualificationQuery.class)))
                .thenReturn(Optional.of(ready));
        when(readyPlan.missingFilters()).thenReturn(List.of());
        when(readyPlan.missingTargets()).thenReturn(List.of());
        when(readyPlan.effectiveQuery()).thenReturn("football boots");
        when(readyPlan.assistantMessage()).thenReturn("Ready");
        when(mapper.map(readyPlan)).thenReturn(filters);
        var service = new UserProductSearchAgentQualificationService(
                qualificationService, persistenceService, mapper);

        var result = service.qualify(
                profile,
                conversationId,
                null,
                "Size 10, shipping to the United States"
        );

        ArgumentCaptor<FindPendingUserProductSearchQualificationQuery> pendingQuery =
                ArgumentCaptor.forClass(FindPendingUserProductSearchQualificationQuery.class);
        verify(persistenceService).findLatestPending(pendingQuery.capture());
        assertThat(pendingQuery.getValue().conversationId()).isEqualTo(conversationId);
        ArgumentCaptor<QualifyUserProductSearchCommand> command =
                ArgumentCaptor.forClass(QualifyUserProductSearchCommand.class);
        verify(qualificationService).qualify(org.mockito.ArgumentMatchers.eq(profile), command.capture());
        assertThat(command.getValue().qualificationId()).isEqualTo(qualificationId);
        assertThat(command.getValue().message()).isEqualTo("Size 10, shipping to the United States");
        assertThat(result.qualificationId()).isEqualTo(qualificationId);
        assertThat(result.ready()).isTrue();
        assertThat(result.filters()).isSameAs(filters);
    }

    private UserProductSearchQualificationSnapshot snapshot(
            UUID qualificationId,
            UUID userId,
            UUID conversationId,
            UserProductSearchQualificationStatus status,
            UserProductSearchQualificationPlan plan
    ) {
        return new UserProductSearchQualificationSnapshot(
                qualificationId,
                userId,
                conversationId,
                null,
                "football boots",
                status,
                plan,
                "model",
                "prompt",
                Instant.EPOCH,
                Instant.EPOCH
        );
    }
}
