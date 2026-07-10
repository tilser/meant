package com.meant.api.plugin.catalog.common.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.OfferComponentIdentity;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.OfferMerchantScope;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.SellingPlanIdentity;
import com.meant.api.plugin.catalog.common.dto.SellingPlanOption;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CanonicalCommerceKeyTest {

    private static final ProviderIdentity PROVIDER = new ProviderIdentity("future_provider");

    @Test
    void lengthPrefixedFieldsPreventStructuralAndDelimiterCollisions() {
        OfferIdentity first = identity("merchant|product", "variant", List.of(), List.of(), null);
        OfferIdentity second = new OfferIdentity(
                PROVIDER,
                OfferMerchantScope.external(identifier(ExternalIdentifierType.MERCHANT, "merchant")),
                identifier(ExternalIdentifierType.PRODUCT, "product|variant"),
                null,
                List.of(),
                List.of(),
                null
        );

        assertThat(first.key()).isNotEqualTo(second.key());
    }

    @Test
    void optionalBlankAndNullVariantNormalizeToTheSameIdentity() {
        ExternalIdentifier blankVariant = ExternalIdentifier.optional(
                ExternalIdentifierType.VARIANT,
                PROVIDER.value(),
                "   "
        );

        assertThat(blankVariant).isNull();
        assertThat(identityWithVariant("merchant", blankVariant, List.of(), List.of(), null).key())
                .isEqualTo(identity("merchant", null, List.of(), List.of(), null).key());
    }

    @Test
    void providerDefinedIdentityValuesRemainCaseSensitive() {
        assertThat(identity("merchant", "Product-A", List.of(), List.of(), null).key())
                .isNotEqualTo(identity("merchant", "product-a", List.of(), List.of(), null).key());
    }

    @Test
    void selectedOptionAndComponentOrderAreCanonicalButValuesAndComponentsRemainDistinct() {
        List<ProductAttribute> options = List.of(
                new ProductAttribute(null, "Size", "M"),
                new ProductAttribute(null, "Color", "Blue")
        );
        OfferComponentIdentity shirt = component("shirt", "shirt-blue", 1);
        OfferComponentIdentity belt = component("belt", "belt-brown", 1);
        OfferIdentity first = identity("merchant", "variant", options, List.of(shirt, belt), null);
        OfferIdentity reordered = identity(
                "merchant",
                "variant",
                List.of(options.get(1), options.get(0)),
                List.of(belt, shirt),
                null
        );
        OfferIdentity differentOption = identity(
                "merchant",
                "variant",
                List.of(new ProductAttribute(null, "Color", "Red"), options.getFirst()),
                List.of(shirt, belt),
                null
        );
        OfferIdentity differentComponent = identity(
                "merchant",
                "variant",
                options,
                List.of(shirt, component("hat", "hat-blue", 1)),
                null
        );

        assertThat(first.key()).isEqualTo(reordered.key());
        assertThat(first.key()).isNotEqualTo(differentOption.key()).isNotEqualTo(differentComponent.key());
    }

    @Test
    void sellingPlanOptionOrderDoesNotChangeTheKey() {
        SellingPlanIdentity first = sellingPlan(List.of(
                new SellingPlanOption("frequency", "monthly"),
                new SellingPlanOption("prepay", "false")
        ));
        SellingPlanIdentity reversed = sellingPlan(List.of(
                new SellingPlanOption("prepay", "false"),
                new SellingPlanOption("frequency", "monthly")
        ));

        assertThat(identity("merchant", "variant", List.of(), List.of(), first).key())
                .isEqualTo(identity("merchant", "variant", List.of(), List.of(), reversed).key());
    }

    @Test
    void externalSellerAndLocalFallbackScopesAreCollisionSafe() {
        OfferIdentity externalSellerOne = identity("merchant-1", "variant", List.of(), List.of(), null);
        OfferIdentity externalSellerTwo = identity("merchant-2", "variant", List.of(), List.of(), null);
        OfferIdentity localFallbackOne = localFallbackIdentity(
                UUID.fromString("10000000-0000-0000-0000-000000000001"));
        OfferIdentity localFallbackTwo = localFallbackIdentity(
                UUID.fromString("10000000-0000-0000-0000-000000000002"));

        assertThat(externalSellerOne.key()).isNotEqualTo(externalSellerTwo.key());
        assertThat(localFallbackOne.key()).isNotEqualTo(localFallbackTwo.key());
        assertThat(externalSellerOne.key()).isNotEqualTo(localFallbackOne.key());
    }

    @Test
    void stableKeysAreDeterministicAndVersionedForTheNewIdentityMeaning() {
        OfferIdentity identity = identity("gid://provider/Merchant/1", "variant-2", List.of(), List.of(), null);

        assertThat(identity.key())
                .isEqualTo(identity.key())
                .startsWith("offer_v2_")
                .hasSize(73);
        assertThat(CanonicalCommerceKey.fallbackProductKey(identity)).startsWith("product_v2_");
    }

    private OfferIdentity identity(
            String merchant,
            String variant,
            List<ProductAttribute> selectedOptions,
            List<OfferComponentIdentity> components,
            SellingPlanIdentity sellingPlan
    ) {
        return identityWithVariant(
                merchant,
                ExternalIdentifier.optional(ExternalIdentifierType.VARIANT, PROVIDER.value(), variant),
                selectedOptions,
                components,
                sellingPlan
        );
    }

    private OfferIdentity identityWithVariant(
            String merchant,
            ExternalIdentifier variant,
            List<ProductAttribute> selectedOptions,
            List<OfferComponentIdentity> components,
            SellingPlanIdentity sellingPlan
    ) {
        return new OfferIdentity(
                PROVIDER,
                OfferMerchantScope.external(identifier(ExternalIdentifierType.MERCHANT, merchant)),
                identifier(ExternalIdentifierType.PRODUCT, "product"),
                variant,
                selectedOptions,
                components,
                sellingPlan
        );
    }

    private OfferIdentity localFallbackIdentity(UUID integrationId) {
        return new OfferIdentity(
                PROVIDER,
                OfferMerchantScope.localIntegrationFallback(integrationId),
                identifier(ExternalIdentifierType.PRODUCT, "product"),
                identifier(ExternalIdentifierType.VARIANT, "variant"),
                List.of(),
                List.of(),
                null
        );
    }

    private OfferComponentIdentity component(String product, String variant, int quantity) {
        return new OfferComponentIdentity(
                identifier(ExternalIdentifierType.PRODUCT, product),
                identifier(ExternalIdentifierType.VARIANT, variant),
                quantity,
                List.of()
        );
    }

    private SellingPlanIdentity sellingPlan(List<SellingPlanOption> options) {
        return new SellingPlanIdentity(
                identifier(ExternalIdentifierType.SELLING_PLAN_GROUP, "subscriptions"),
                identifier(ExternalIdentifierType.SELLING_PLAN, "monthly"),
                options
        );
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, PROVIDER.value(), value);
    }
}
