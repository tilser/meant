package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.entity.UserDiscoverProductResultItem;
import com.meant.api.module.user.entity.UserDiscoverProductResultSet;
import com.meant.api.module.user.entity.UserProductSearchQualification;
import com.meant.api.module.user.repository.UserDiscoverProductResultItemRepository;
import com.meant.api.module.user.repository.UserDiscoverProductResultSetRepository;
import com.meant.api.module.user.repository.UserProductSearchQualificationRepository;
import com.meant.api.module.user.service.command.ReplaceUserDiscoverProductResultSetCommand;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserDiscoverProductResultSetPersistenceServiceTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CONVERSATION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID QUALIFICATION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void rejectsInconsistentPaginationBeforePersistence() {
        assertThatThrownBy(() -> new ReplaceUserDiscoverProductResultSetCommand(
                USER_ID, CONVERSATION_ID, QUALIFICATION_ID, 0, 10, null, true, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("next offset");
        assertThatThrownBy(() -> new ReplaceUserDiscoverProductResultSetCommand(
                USER_ID, CONVERSATION_ID, QUALIFICATION_ID, 10, 10, 10, true, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater");
    }

    @Test
    void atomicallyReplacesOnlyOrderedCanonicalIdentifiersForOneQualifiedPage() {
        UserDiscoverProductResultSetRepository resultSets = mock(UserDiscoverProductResultSetRepository.class);
        UserDiscoverProductResultItemRepository items = mock(UserDiscoverProductResultItemRepository.class);
        UserProductSearchQualificationRepository qualifications = mock(UserProductSearchQualificationRepository.class);
        UserCanonicalProductReferencePersistenceService references =
                mock(UserCanonicalProductReferencePersistenceService.class);
        UserDiscoverProductResultSetPersistenceService service = new UserDiscoverProductResultSetPersistenceService(
                resultSets, items, qualifications, references);
        when(qualifications.findByIdForUpdate(QUALIFICATION_ID)).thenReturn(Optional.of(qualification()));
        when(resultSets.findByQualificationIdAndPageOffsetAndResultLimit(QUALIFICATION_ID, 5, 10))
                .thenReturn(Optional.empty());
        when(resultSets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CanonicalProduct first = product("canonical-first");
        CanonicalProduct second = product("canonical-second");

        UUID resultSetId = service.replace(new ReplaceUserDiscoverProductResultSetCommand(
                USER_ID,
                CONVERSATION_ID,
                QUALIFICATION_ID,
                5,
                10,
                15,
                true,
                false,
                List.of(first, second, first)
        ));

        assertThat(resultSetId).isNotNull();
        verify(references).replace(USER_ID, List.of(first, second));
        ArgumentCaptor<Iterable<UserDiscoverProductResultItem>> savedItems = ArgumentCaptor.forClass(Iterable.class);
        verify(items).saveAll(savedItems.capture());
        List<UserDiscoverProductResultItem> persistedItems = new ArrayList<>();
        savedItems.getValue().forEach(persistedItems::add);
        assertThat(persistedItems).extracting(
                UserDiscoverProductResultItem::getCanonicalProductKey,
                UserDiscoverProductResultItem::getResultRank
        ).containsExactly(
                org.assertj.core.groups.Tuple.tuple("canonical-first", 5),
                org.assertj.core.groups.Tuple.tuple("canonical-second", 6)
        );
        var itemOrder = inOrder(items);
        itemOrder.verify(items).deleteByResultSetId(resultSetId);
        itemOrder.verify(items).flush();
        itemOrder.verify(items).saveAll(any());
    }

    @Test
    void refreshesTheStablePageHandleBeforeReplacingItsItems() {
        UserDiscoverProductResultSetRepository resultSets = mock(UserDiscoverProductResultSetRepository.class);
        UserDiscoverProductResultItemRepository items = mock(UserDiscoverProductResultItemRepository.class);
        UserProductSearchQualificationRepository qualifications = mock(UserProductSearchQualificationRepository.class);
        UserCanonicalProductReferencePersistenceService references =
                mock(UserCanonicalProductReferencePersistenceService.class);
        UserDiscoverProductResultSetPersistenceService service = new UserDiscoverProductResultSetPersistenceService(
                resultSets, items, qualifications, references);
        when(qualifications.findByIdForUpdate(QUALIFICATION_ID)).thenReturn(Optional.of(qualification()));
        UserDiscoverProductResultSet existing = UserDiscoverProductResultSet.create(
                USER_ID, CONVERSATION_ID, QUALIFICATION_ID, 0, 10, 0, null, false, false, Instant.now());
        when(resultSets.findByQualificationIdAndPageOffsetAndResultLimit(QUALIFICATION_ID, 0, 10))
                .thenReturn(Optional.of(existing));
        when(resultSets.save(existing)).thenReturn(existing);

        UUID resultSetId = service.replace(new ReplaceUserDiscoverProductResultSetCommand(
                USER_ID, CONVERSATION_ID, QUALIFICATION_ID, 0, 10, null, false, true,
                List.of(product("canonical-only"))
        ));

        assertThat(resultSetId).isEqualTo(existing.getId());
        assertThat(existing.getResultCount()).isOne();
        assertThat(existing.isUpstreamTruncated()).isTrue();
        assertThat(existing.getUpdatedAt()).isAfterOrEqualTo(existing.getCreatedAt());
    }

    private UserProductSearchQualification qualification() {
        return UserProductSearchQualification.create(
                QUALIFICATION_ID,
                USER_ID,
                CONVERSATION_ID,
                null,
                "shoes",
                UserProductSearchQualificationStatus.READY,
                "{}",
                "test-model",
                "test-prompt",
                Instant.parse("2026-07-18T08:00:00Z")
        );
    }

    private CanonicalProduct product(String key) {
        CanonicalProduct product = mock(CanonicalProduct.class);
        when(product.key()).thenReturn(key);
        return product;
    }
}
