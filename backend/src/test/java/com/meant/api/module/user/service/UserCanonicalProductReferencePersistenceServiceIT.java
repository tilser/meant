package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.catalog.service.CatalogDataUsePolicyMetrics;
import com.meant.api.module.catalog.service.CatalogDataUsePolicyResolver;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogPayloadClass;
import com.meant.api.module.catalog.service.dto.CatalogRetentionDecision;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.port.CatalogDataUsePolicy;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.entity.UserCanonicalProductReference;
import com.meant.api.module.user.entity.UserCanonicalProductReference.DurableReferenceSnapshot;
import com.meant.api.module.user.repository.UserCanonicalProductReferenceRepository;
import com.meant.api.module.user.repository.UserRepository;
import com.meant.api.provider.shopify.catalog.ShopifyOfferIdentityStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class UserCanonicalProductReferencePersistenceServiceIT extends PostgresIntegrationTestSupport {

    private static final ProviderIdentity PROVIDER = new ProviderIdentity("SHOPIFY");
    private static final DiscoverySourceIdentity SOURCE = new DiscoverySourceIdentity(
            PROVIDER,
            ResultSourceType.PROVIDER_CATALOG,
            "SHOPIFY_GLOBAL"
    );
    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCanonicalProductReferenceRepository referenceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void multiKeyReplacementUsesTheLastDuplicateAndPreservesUnrelatedReferences() {
        User targetUser = user("canonical-target@example.com");
        User otherUser = user("canonical-other@example.com");
        referenceRepository.saveAllAndFlush(List.of(
                storedReference(targetUser.getId(), "product-a", "old-a"),
                storedReference(targetUser.getId(), "product-b", "old-b"),
                storedReference(targetUser.getId(), "product-c", "preserved-c"),
                storedReference(otherUser.getId(), "product-a", "preserved-other-user")
        ));

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();
        UserCanonicalProductReferencePersistenceService service = service();
        service.replace(targetUser.getId(), List.of(
                product("product-a", "first"),
                productWithoutDurableReferences("product-b"),
                product("product-a", "last")
        ));
        entityManager.flush();

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(references(targetUser.getId(), "product-a"))
                .singleElement()
                .satisfies(reference -> {
                    assertThat(reference.getExternalVariantId()).isEqualTo("variant-last");
                    assertThat(reference.isNew()).isFalse();
                });
        assertThat(references(targetUser.getId(), "product-b")).isEmpty();
        assertThat(references(targetUser.getId(), "product-c"))
                .extracting(UserCanonicalProductReference::getOfferKey)
                .containsExactly("preserved-c");
        assertThat(references(otherUser.getId(), "product-a"))
                .extracting(UserCanonicalProductReference::getOfferKey)
                .containsExactly("preserved-other-user");
    }

    private UserCanonicalProductReferencePersistenceService service() {
        CatalogDataUsePolicy policy = new CatalogDataUsePolicy() {
            @Override
            public boolean supports(DiscoverySourceIdentity source) {
                return SOURCE.equals(source);
            }

            @Override
            public CatalogRetentionDecision decide(
                    DiscoverySourceIdentity source,
                    CatalogPayloadClass payloadClass
            ) {
                return CatalogRetentionDecision.identifiersOnly("policy-v1");
            }
        };
        return new UserCanonicalProductReferencePersistenceService(
                referenceRepository,
                new CatalogDataUsePolicyResolver(
                        List.of(policy),
                        new CatalogDataUsePolicyMetrics(new SimpleMeterRegistry())
                ),
                new ObjectMapper(),
                List.of(new ShopifyOfferIdentityStrategy())
        );
    }

    private CanonicalProduct product(String productKey, String suffix) {
        return product(productKey, suffix, true);
    }

    private CanonicalProduct product(String productKey, String suffix, boolean durableMerchantReference) {
        ExternalIdentifier merchant = identifier(ExternalIdentifierType.MERCHANT, "merchant-" + suffix);
        ExternalIdentifier product = identifier(ExternalIdentifierType.PRODUCT, "product-" + suffix);
        ExternalIdentifier variant = identifier(ExternalIdentifierType.VARIANT, "variant-" + suffix);
        ProductAttribute color = new ProductAttribute("variant-option", "Color", "Blue");
        ResultProvenance provenance = new ResultProvenance(
                PROVIDER,
                SOURCE,
                null,
                durableMerchantReference ? merchant : null,
                "shop.example",
                product,
                variant,
                new ResultFreshness(NOW, null),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "fixture", null)
        );
        Offer offer = new Offer(
                new OfferIdentity(
                        PROVIDER,
                        OfferMerchantScope.external(merchant),
                        new ShopifyOfferIdentityStrategy().product(PROVIDER, product, variant),
                        variant,
                        List.of(color),
                        List.of(),
                        null
                ),
                "Fixture shop",
                "Blue",
                null,
                null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 3, null),
                List.of(),
                null,
                List.of(provenance)
        );
        return new CanonicalProduct(
                productKey,
                "Title",
                "Description",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(provenance),
                List.of(offer)
        );
    }

    private CanonicalProduct productWithoutDurableReferences(String productKey) {
        return product(productKey, "without-durable-reference", false);
    }

    private UserCanonicalProductReference storedReference(UUID userId, String productKey, String offerKey) {
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
                        "policy-v1"
                ),
                NOW
        );
    }

    private List<UserCanonicalProductReference> references(UUID userId, String productKey) {
        return referenceRepository.findByUserIdAndCanonicalProductKeyOrderByOfferRankAscIdAsc(
                userId,
                productKey
        );
    }

    private User user(String email) {
        return userRepository.saveAndFlush(User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .newsletter(false)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build());
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, PROVIDER.value(), value);
    }
}
