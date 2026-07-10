package com.meant.api.plugin.catalog.common.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.SellingPlanIdentity;
import com.meant.api.plugin.catalog.common.dto.SellingPlanOption;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CanonicalCommerceKeyTest {

    private static final UUID INTEGRATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final ProviderIdentity PROVIDER = new ProviderIdentity("future_provider");

    @Test
    void lengthPrefixedFieldsPreventDelimiterCollisions() {
        OfferIdentity first = identity("merchant|product", "variant", null, null);
        OfferIdentity second = identity("merchant", "product|variant", null, null);

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
        assertThat(identityWithVariant("merchant", "product", blankVariant, null).key())
                .isEqualTo(identity("merchant", "product", null, null).key());
    }

    @Test
    void providerDefinedIdentityValuesRemainCaseSensitive() {
        assertThat(identity("merchant", "Product-A", null, null).key())
                .isNotEqualTo(identity("merchant", "product-a", null, null).key());
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

        assertThat(identity("merchant", "product", null, first).key())
                .isEqualTo(identity("merchant", "product", null, reversed).key());
    }

    @Test
    void stableKeyIsDeterministic() {
        OfferIdentity identity = identity("gid://provider/Merchant/1", "gid://provider/Product/2", null, null);

        assertThat(identity.key())
                .isEqualTo(identity.key())
                .startsWith("offer_v1_")
                .hasSize(73);
    }

    private OfferIdentity identityWithVariant(
            String merchant,
            String product,
            ExternalIdentifier variant,
            SellingPlanIdentity sellingPlan
    ) {
        return new OfferIdentity(
                PROVIDER,
                INTEGRATION_ID,
                identifier(ExternalIdentifierType.MERCHANT, merchant),
                identifier(ExternalIdentifierType.PRODUCT, product),
                variant,
                sellingPlan
        );
    }

    private OfferIdentity identity(String merchant, String product, String variant, SellingPlanIdentity sellingPlan) {
        return identityWithVariant(
                merchant,
                product,
                ExternalIdentifier.optional(ExternalIdentifierType.VARIANT, PROVIDER.value(), variant),
                sellingPlan
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
