package com.meant.api.module.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.entity.UserCanonicalProductReference;
import com.meant.api.module.user.entity.UserCanonicalProductReference.DurableReferenceSnapshot;
import com.meant.api.module.user.entity.UserInventoryItem;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserRepositoryQueryOptimizationIT extends PostgresIntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserInventoryItemRepository inventoryItemRepository;

    @Autowired
    private UserCanonicalProductReferenceRepository canonicalProductReferenceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void inventorySummaryReturnsCountAndLatestUpdateFromOneAggregateQuery() {
        User user = user("inventory-summary@example.com");
        Instant older = Instant.parse("2026-07-01T10:00:00Z");
        Instant newer = Instant.parse("2026-07-02T12:30:00Z");

        assertThat(inventoryItemRepository.summarizeProfileByUserId(user.getId()))
                .satisfies(summary -> {
                    assertThat(summary.itemCount()).isZero();
                    assertThat(summary.lastUpdatedAt()).isNull();
                });

        inventoryItemRepository.saveAllAndFlush(List.of(
                inventoryItem(user.getId(), "older", older),
                inventoryItem(user.getId(), "newer", newer)
        ));

        assertThat(inventoryItemRepository.summarizeProfileByUserId(user.getId()))
                .satisfies(summary -> {
                    assertThat(summary.itemCount()).isEqualTo(2);
                    assertThat(summary.lastUpdatedAt()).isEqualTo(newer);
                });
    }

    @Test
    void bulkReferenceDeletePreservesUnrelatedRowsAndTheCallingPersistenceContext() {
        User user = user("reference-delete@example.com");
        Instant now = Instant.parse("2026-07-03T09:15:00Z");
        canonicalProductReferenceRepository.saveAllAndFlush(List.of(
                reference(user.getId(), "product-a", "offer-a1", now),
                reference(user.getId(), "product-a", "offer-a2", now),
                reference(user.getId(), "product-b", "offer-b1", now)
        ));

        assertThat(entityManager.contains(user)).isTrue();
        assertThat(canonicalProductReferenceRepository.deleteByUserIdAndCanonicalProductKeyIn(
                user.getId(),
                List.of("product-a")
        )).isEqualTo(2);

        assertThat(entityManager.contains(user)).isTrue();
        assertThat(canonicalProductReferenceRepository
                .findByUserIdAndCanonicalProductKeyOrderByOfferRankAscIdAsc(user.getId(), "product-a"))
                .isEmpty();
        assertThat(canonicalProductReferenceRepository
                .findByUserIdAndCanonicalProductKeyOrderByOfferRankAscIdAsc(user.getId(), "product-b"))
                .extracting(UserCanonicalProductReference::getOfferKey)
                .containsExactly("offer-b1");
    }

    private User user(String email) {
        Instant now = Instant.parse("2026-07-01T08:00:00Z");
        return userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .newsletter(false)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private UserInventoryItem inventoryItem(UUID userId, String sourceProductKey, Instant updatedAt) {
        return UserInventoryItem.create(
                userId,
                UserInventoryItem.Snapshot.builder()
                        .source(UserInventorySource.MANUAL)
                        .sourceProductKey(sourceProductKey)
                        .name(sourceProductKey)
                        .category(UserInventoryCategory.OTHER)
                        .quantity(1)
                        .attributes("{}")
                        .build(),
                updatedAt
        );
    }

    private UserCanonicalProductReference reference(
            UUID userId,
            String productKey,
            String offerKey,
            Instant now
    ) {
        return UserCanonicalProductReference.create(
                userId,
                productKey,
                offerKey,
                0,
                new DurableReferenceSnapshot(
                        "provider",
                        "source-type",
                        "source-identity",
                        null,
                        null,
                        null,
                        "merchant.example",
                        "external-product",
                        null,
                        "[]",
                        "[]",
                        null,
                        "retention-policy"
                ),
                now
        );
    }
}
