package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductCertification;
import com.meant.api.module.catalog.service.dto.ProductMaterial;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserCanonicalProductPersonalizationTest {

    private static final ProviderIdentity PROVIDER = new ProviderIdentity("TEST");
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-17T08:00:00Z");

    private final UserProductPreferenceMatchCuratorService service =
            new UserProductPreferenceMatchCuratorService();

    @Test
    void explainsCanonicalProductWithOnlyExplicitlySupportedFacts() {
        CanonicalProduct product = product(
                "organic-granola",
                "Organic Granola",
                "A certified gluten-free breakfast.",
                List.of(new ProductAttribute("dietary", "Dietary claim", "Gluten-free")),
                List.of(),
                List.of(new ProductCertification("USDA Organic", "USDA", null, null))
        );

        UserCanonicalProductPersonalizationResult result = service.curateCanonical(
                List.of(product),
                settings(
                        filter("organic", "Organic", "Prefer organic products.", "prefer", 10),
                        filter("gluten-free", "Gluten-free", "Require products without gluten.", "require", 20),
                        filter("crypto", "Crypto", "Prefer crypto references.", "prefer", 30)
                )
        ).get(product.key());

        assertThat(result.matchedFilterIds()).containsExactly("organic", "gluten-free");
        assertThat(result.missedFilterIds()).isEmpty();
        assertThat(result.whyMeantForYou())
                .isEqualTo("Product details list organic and gluten-free, matching your saved preferences.")
                .doesNotContain("rank", "score", "crypto");
    }

    @Test
    void reportsAvoidedMaterialOnlyWhenCanonicalEvidenceListsIt() {
        CanonicalProduct product = product(
                "training-tee",
                "Training Tee",
                "Quick-drying jersey.",
                List.of(),
                List.of(new ProductMaterial("Polyester", 10_000)),
                List.of()
        );

        UserCanonicalProductPersonalizationResult result = service.curateCanonical(
                List.of(product),
                settings(filter("no-polyester", "No polyester", "Avoid polyester.", "avoid", 10))
        ).get(product.key());

        assertThat(result.matchedFilterIds()).isEmpty();
        assertThat(result.missedFilterIds()).containsExactly("no-polyester");
        assertThat(result.whyMeantForYou())
                .isEqualTo("Product details list polyester, which may conflict with your saved preference.");
    }

    @Test
    void fallsBackToSearchRelevanceWithoutClaimingAnUnsupportedPreferenceMatch() {
        CanonicalProduct product = product(
                "desk-lamp",
                "Desk Lamp",
                "An adjustable task light.",
                List.of(),
                List.of(),
                List.of()
        );

        UserCanonicalProductPersonalizationResult result = service.curateCanonical(
                List.of(product),
                settings(filter("organic", "Organic", "Prefer organic products.", "prefer", 10))
        ).get(product.key());

        assertThat(result.matchedFilterIds()).isEmpty();
        assertThat(result.missedFilterIds()).isEmpty();
        assertThat(result.whyMeantForYou())
                .isEqualTo("This looks relevant to your search based on the available product details.");
    }

    @Test
    void doesNotTreatNotOrganicDescriptionTextAsPositiveOrganicEvidence() {
        CanonicalProduct product = product(
                "conventional-snack",
                "Conventional Snack",
                "This product is not organic.",
                List.of(),
                List.of(),
                List.of()
        );

        UserCanonicalProductPersonalizationResult result = service.curateCanonical(
                List.of(product),
                settings(filter("organic", "Organic", "Prefer organic products.", "prefer", 10))
        ).get(product.key());

        assertThat(result.matchedFilterIds()).isEmpty();
        assertThat(result.whyMeantForYou())
                .isEqualTo("This looks relevant to your search based on the available product details.");
    }

    @Test
    void doesNotTreatStraightApostropheNegativeContractionAsPositiveOrganicEvidence() {
        CanonicalProduct product = product(
                "conventional-snack-contraction",
                "Conventional Snack",
                "This product isn't organic.",
                List.of(),
                List.of(),
                List.of()
        );

        UserCanonicalProductPersonalizationResult result = service.curateCanonical(
                List.of(product),
                settings(filter("organic", "Organic", "Prefer organic products.", "prefer", 10))
        ).get(product.key());

        assertThat(result.matchedFilterIds()).isEmpty();
        assertThat(result.whyMeantForYou())
                .isEqualTo("This looks relevant to your search based on the available product details.");
    }

    @Test
    void doesNotTreatNegativeOrganicAttributeAsPositiveOrganicEvidence() {
        CanonicalProduct product = product(
                "non-organic-snack",
                "Conventional Snack",
                null,
                List.of(new ProductAttribute("claims", "Organic", "No")),
                List.of(),
                List.of()
        );

        UserCanonicalProductPersonalizationResult result = service.curateCanonical(
                List.of(product),
                settings(filter("organic", "Organic", "Prefer organic products.", "prefer", 10))
        ).get(product.key());

        assertThat(result.matchedFilterIds()).isEmpty();
        assertThat(result.whyMeantForYou())
                .isEqualTo("This looks relevant to your search based on the available product details.");
    }

    @Test
    void distinguishesNegatedGlutenFreeTextFromExplicitGlutenFreeEvidence() {
        ShoppingFilterResult glutenFree = filter(
                "gluten-free",
                "Gluten-free",
                "Require products without gluten.",
                "require",
                10
        );
        CanonicalProduct negated = product(
                "wheat-snack",
                "Wheat Snack",
                "This product is not gluten-free.",
                List.of(),
                List.of(),
                List.of()
        );
        CanonicalProduct explicit = product(
                "rice-snack",
                "Rice Snack",
                "This product is gluten-free.",
                List.of(),
                List.of(),
                List.of()
        );

        var results = service.curateCanonical(List.of(negated, explicit), settings(glutenFree));

        assertThat(results.get(negated.key()).matchedFilterIds()).isEmpty();
        assertThat(results.get(negated.key()).whyMeantForYou())
                .isEqualTo("This looks relevant to your search based on the available product details.");
        assertThat(results.get(explicit.key()).matchedFilterIds()).containsExactly("gluten-free");
        assertThat(results.get(explicit.key()).whyMeantForYou())
                .isEqualTo("Product details list gluten-free, matching your saved preference.");
    }

    @Test
    void doesNotTreatCurlyApostropheNegativeContractionAsPositiveGlutenFreeEvidence() {
        CanonicalProduct product = product(
                "wheat-snack-contraction",
                "Wheat Snack",
                "This product isn’t gluten-free.",
                List.of(),
                List.of(),
                List.of()
        );

        UserCanonicalProductPersonalizationResult result = service.curateCanonical(
                List.of(product),
                settings(filter(
                        "gluten-free",
                        "Gluten-free",
                        "Require products without gluten.",
                        "require",
                        10
                ))
        ).get(product.key());

        assertThat(result.matchedFilterIds()).isEmpty();
        assertThat(result.whyMeantForYou())
                .isEqualTo("This looks relevant to your search based on the available product details.");
    }

    private UserSettingsResult settings(ShoppingFilterResult... filters) {
        return new UserSettingsResult(
                null,
                null,
                null,
                List.of(),
                List.of(filters),
                List.of(),
                List.of(),
                List.of(),
                OBSERVED_AT,
                OBSERVED_AT
        );
    }

    private ShoppingFilterResult filter(
            String id,
            String label,
            String description,
            String polarity,
            int displayOrder
    ) {
        return new ShoppingFilterResult(id, label, description, "shopping", polarity, displayOrder);
    }

    private CanonicalProduct product(
            String key,
            String title,
            String description,
            List<ProductAttribute> attributes,
            List<ProductMaterial> materials,
            List<ProductCertification> certifications
    ) {
        ExternalIdentifier merchant = identifier(ExternalIdentifierType.MERCHANT, "merchant");
        ExternalIdentifier product = identifier(ExternalIdentifierType.PRODUCT, key);
        ExternalIdentifier variant = identifier(ExternalIdentifierType.VARIANT, key + "-variant");
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                PROVIDER,
                ResultSourceType.PROVIDER_CATALOG,
                "TEST_CATALOG"
        );
        ResultProvenance provenance = new ResultProvenance(
                PROVIDER,
                source,
                null,
                merchant,
                product,
                variant,
                new ResultFreshness(OBSERVED_AT, null),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "fixture", null)
        );
        Offer offer = new Offer(
                new OfferIdentity(
                        PROVIDER,
                        OfferMerchantScope.external(merchant),
                        product,
                        variant,
                        List.of(),
                        List.of(),
                        null
                ),
                "Fixture merchant",
                null,
                new Money(1_000, "USD"),
                null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, null, null),
                List.of(),
                null,
                List.of(provenance)
        );
        return new CanonicalProduct(
                key,
                title,
                description,
                List.of(),
                attributes,
                materials,
                certifications,
                List.of(),
                List.of(),
                List.of(provenance),
                List.of(offer)
        );
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, PROVIDER.value(), value);
    }
}
