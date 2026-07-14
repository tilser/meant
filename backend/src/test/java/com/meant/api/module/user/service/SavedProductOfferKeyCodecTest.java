package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.entity.UserSavedProduct.UserSavedProductBuilder;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SavedProductOfferKeyCodecTest {
    private static final UUID SAVED_PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000124");
    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000125");
    private static final UUID INTEGRATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000126");

    @Test
    void roundTripsAndVerifiesThePersistedReferenceWithinTheCartKeyLimit() {
        UserSavedProduct savedProduct = savedProduct(builder -> { });

        String key = SavedProductOfferKeyCodec.encode(savedProduct);
        SavedProductOfferKeyCodec.Selection selection = SavedProductOfferKeyCodec.decode(key).orElseThrow();

        assertThat(key).startsWith(SavedProductOfferKeyCodec.PREFIX).hasSizeLessThan(200);
        assertThat(selection.savedProductId()).isEqualTo(SAVED_PRODUCT_ID);
        assertThat(selection.referenceFingerprint()).hasSize(43);
        assertThat(SavedProductOfferKeyCodec.verify(selection, savedProduct)).isTrue();
    }

    @Test
    void fingerprintBindsEveryPersistedDurableIdentityAndPolicyField() {
        UserSavedProduct baseline = savedProduct(builder -> { });
        String fingerprint = SavedProductOfferKeyCodec.fingerprint(baseline);
        List<UserSavedProduct> mutations = List.of(
                savedProduct(builder -> builder.userId(UUID.randomUUID())),
                savedProduct(builder -> builder.productKey("different-product-key")),
                savedProduct(builder -> builder.sourceProvider("DIFFERENT_PROVIDER")),
                savedProduct(builder -> builder.sourceType(ResultSourceType.PROVIDER_CATALOG.name())),
                savedProduct(builder -> builder.sourceIdentity("different-source")),
                savedProduct(builder -> builder.localMerchantId(UUID.randomUUID())),
                savedProduct(builder -> builder.merchantIntegrationId(UUID.randomUUID())),
                savedProduct(builder -> builder.externalMerchantId("different-merchant")),
                savedProduct(builder -> builder.externalMerchantDomain("different.example")),
                savedProduct(builder -> builder.externalProductId("different-product")),
                savedProduct(builder -> builder.externalVariantId("different-variant")),
                savedProduct(builder -> builder.selectedOptionsJson("[{\"name\":\"Size\",\"value\":\"M\"}]")),
                savedProduct(builder -> builder.componentsJson("[{\"quantity\":2}]")),
                savedProduct(builder -> builder.sellingPlanJson("{\"planReference\":\"monthly\"}")),
                savedProduct(builder -> builder.retentionPolicyKey("different-policy"))
        );

        assertThat(mutations)
                .extracting(SavedProductOfferKeyCodec::fingerprint)
                .doesNotContain(fingerprint);
        assertThat(mutations).allSatisfy(mutation ->
                assertThat(SavedProductOfferKeyCodec.verify(
                        SavedProductOfferKeyCodec.decode(SavedProductOfferKeyCodec.encode(baseline)).orElseThrow(),
                        mutation
                )).isFalse());
    }

    @Test
    void verificationIgnoresReferenceTimestampButRejectsAnotherRowId() {
        UserSavedProduct baseline = savedProduct(builder -> { });
        SavedProductOfferKeyCodec.Selection selection = SavedProductOfferKeyCodec.decode(
                SavedProductOfferKeyCodec.encode(baseline)).orElseThrow();
        UserSavedProduct refreshed = savedProduct(builder -> builder.referenceVerifiedAt(
                Instant.parse("2026-07-12T00:00:00Z")));
        UserSavedProduct anotherRow = savedProduct(builder -> builder.id(UUID.randomUUID()));

        assertThat(SavedProductOfferKeyCodec.verify(selection, refreshed)).isTrue();
        assertThat(SavedProductOfferKeyCodec.verify(selection, anotherRow)).isFalse();
    }

    @Test
    void rejectsUnknownVersionsMalformedFingerprintsAndNonCanonicalIds() {
        assertThat(SavedProductOfferKeyCodec.decode(null)).isEmpty();
        assertThat(SavedProductOfferKeyCodec.decode("offer_v2_00000000-0000-0000-0000-000000000123")).isEmpty();
        assertThat(SavedProductOfferKeyCodec.decode("saved_offer_v1_not-a-uuid_fingerprint")).isEmpty();
        assertThat(SavedProductOfferKeyCodec.decode(
                "saved_offer_v1_00000000-0000-0000-0000-000000000123_too-short")).isEmpty();
        assertThat(SavedProductOfferKeyCodec.decode(
                "saved_offer_v1_00000000-0000-0000-0000-000000000123_!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"))
                .isEmpty();
    }

    private UserSavedProduct savedProduct(Consumer<UserSavedProductBuilder> mutation) {
        Instant now = Instant.parse("2026-07-11T00:00:00Z");
        UserSavedProductBuilder builder = UserSavedProduct.builder()
                .id(SAVED_PRODUCT_ID)
                .userId(USER_ID)
                .productKey("product_v3_saved")
                .sourceProvider("GENERIC_UCP")
                .sourceType(ResultSourceType.MERCHANT_STOREFRONT.name())
                .sourceIdentity("merchant-source")
                .localMerchantId(MERCHANT_ID)
                .merchantIntegrationId(INTEGRATION_ID)
                .externalMerchantId("merchant-1")
                .externalMerchantDomain("merchant.example")
                .externalProductId("product-1")
                .externalVariantId("variant-1")
                .selectedOptionsJson("[]")
                .componentsJson("[]")
                .retentionPolicyKey("saved-policy")
                .referenceVerifiedAt(now)
                .createdAt(now)
                .updatedAt(now);
        mutation.accept(builder);
        return builder.build();
    }
}
