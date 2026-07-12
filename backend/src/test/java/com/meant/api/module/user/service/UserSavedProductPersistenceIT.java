package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserSavedProductRepository;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class UserSavedProductPersistenceIT extends PostgresIntegrationTestSupport {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000081");

    @Autowired
    private UserSavedProductPersistenceService persistenceService;

    @Autowired
    private UserSavedProductRepository repository;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UserTasteProfileService tasteProfileService;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        userService.ensureProfile(new EnsureUserProfileCommand(USER_ID, "atomic@example.com", "Atomic", "User"));
    }

    @Test
    void tasteFailureRollsBackSavedMutationAndRetryWritesExactlyOnce() {
        SaveUserProductCommand command = command();
        CatalogProductReference reference = reference();
        doThrow(new UserException("taste failed"))
                .doNothing()
                .when(tasteProfileService)
                .recordSavedProduct(any(), any(), any());

        assertThatThrownBy(() -> persistenceService.save(command, reference, "generic-v2", Instant.now()))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("taste failed");
        assertThat(repository.findByUserIdAndProductKey(USER_ID, command.productKey())).isEmpty();

        persistenceService.save(command, reference, "generic-v2", Instant.now());

        assertThat(repository.findByUserIdAndProductKey(USER_ID, command.productKey())).isPresent();
        assertThat(repository.countByUserIdAndReferenceVerifiedAtIsNotNull(USER_ID)).isEqualTo(1);
    }

    @Test
    void databaseRowStoresPresentationSnapshotButNoCommercialSnapshot() {
        persistenceService.save(command(), reference(), "generic-v2", Instant.now());

        var payload = jdbcTemplate.queryForMap("""
                SELECT product_hash, name, brand, category, tone, image_url, product_url, remote,
                       match_score, price_from, merchant_count, satisfies, misses, note, pros, cons,
                       review_score, review_count, review_insight, offers, needs, provides
                FROM user_saved_products
                WHERE user_id = ?
                """, USER_ID);

        assertThat(payload)
                .containsEntry("product_hash", "hash")
                .containsEntry("name", "Provider title")
                .containsEntry("brand", "Provider brand")
                .containsEntry("category", "Provider category")
                .containsEntry("tone", "#fff")
                .containsEntry("image_url", "https://provider.test/image.jpg")
                .containsEntry("product_url", "https://provider.test/product")
                .containsEntry("remote", true)
                .containsEntry("match_score", 100)
                .containsEntry("merchant_count", 4)
                .containsEntry("note", "Generated note")
                .containsEntry("review_score", 5.0d)
                .containsEntry("review_count", 1000)
                .containsEntry("review_insight", "Review payload")
                .containsEntry("needs", null);
        assertThat(payload.get("satisfies")).isEqualTo("[\"organic\"]");
        assertThat(payload.get("misses")).isEqualTo("[]");
        assertThat(payload.get("pros")).isEqualTo("[\"pro\"]");
        assertThat(payload.get("cons")).isEqualTo("[\"con\"]");
        assertThat(payload.get("provides")).isEqualTo("[]");
        assertThat(payload.get("price_from")).isNull();
        assertThat(payload.get("offers")).isNull();
    }

    private SaveUserProductCommand command() {
        return new SaveUserProductCommand(
                USER_ID, "product-1", "hash", "Provider title", "Provider brand", "Provider category", "#fff",
                "https://provider.test/image.jpg", "https://provider.test/product", true, 100, 0.0d, 4,
                List.of("organic"), List.of(), "Generated note", List.of("pro"), List.of("con"),
                new SaveUserProductCommand.Review(5.0d, 1000, "Review payload"),
                List.of(new SaveUserProductCommand.Offer(
                        "Provider merchant", 0.0d, "Now", null, null, "variant-client", "Client", true)),
                null, List.of(), reference()
        );
    }

    private CatalogProductReference reference() {
        return new CatalogProductReference(
                "product-1",
                MerchantCatalogSourceIdentity.DISCOVERY_SOURCE,
                UUID.fromString("00000000-0000-0000-0000-000000000082"),
                new LocalMerchantRouting(UUID.fromString("00000000-0000-0000-0000-000000000083")),
                null,
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "GENERIC_UCP", "product-1"),
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "GENERIC_UCP", "variant-verified"),
                List.of()
        );
    }
}
