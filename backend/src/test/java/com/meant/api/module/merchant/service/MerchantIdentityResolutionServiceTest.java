package com.meant.api.module.merchant.service;

import static com.meant.api.module.merchant.constant.MerchantIdentityRole.STOREFRONT_DOMAIN;
import static com.meant.api.module.merchant.constant.MerchantIntegrationProvider.SHOPIFY;
import static com.meant.api.module.merchant.constant.MerchantRawSource.HUGGING_FACE;
import static com.meant.api.module.merchant.constant.MerchantRawSource.SHOPIFY_OBSERVATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MerchantIdentityResolutionServiceTest {

    private final UcpProfile profile = new UcpProfile(
            "2026-04-08",
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of()
    );

    @Test
    void providerObservationCannotFallBackToItsSourceDomainAsTheStorefront() {
        MerchantIdentityResolutionService service = new MerchantIdentityResolutionService(
                List.of(context -> Optional.empty()),
                new MerchantDomainNormalizer()
        );
        MerchantRaw observed = MerchantRaw.builder()
                .source(SHOPIFY_OBSERVATION)
                .observedProvider(SHOPIFY)
                .observedExternalMerchantId("gid://shopify/Shop/17756429")
                .domain("youngla.myshopify.com")
                .build();

        assertThatThrownBy(() -> service.resolve(observed, profile))
                .isInstanceOf(MerchantEnrichmentException.class)
                .hasMessageContaining("no verified storefront identity");
    }

    @Test
    void providerMetadataOnAnyRawSourceAlsoDisablesTheFallback() {
        MerchantIdentityResolutionService service = new MerchantIdentityResolutionService(
                List.of(),
                new MerchantDomainNormalizer()
        );
        MerchantRaw malformedDatasetRow = MerchantRaw.builder()
                .source(HUGGING_FACE)
                .observedProvider(SHOPIFY)
                .observedExternalMerchantId("gid://shopify/Shop/17756429")
                .domain("youngla.myshopify.com")
                .build();

        assertThatThrownBy(() -> service.resolve(malformedDatasetRow, profile))
                .isInstanceOf(MerchantEnrichmentException.class)
                .hasMessageContaining("no verified storefront identity");
    }

    @Test
    void datasetTechnicalShopifyDomainCannotBecomeAnUnverifiedStorefront() {
        MerchantIdentityResolutionService service = new MerchantIdentityResolutionService(
                List.of(),
                new MerchantDomainNormalizer()
        );
        MerchantRaw datasetRow = MerchantRaw.builder()
                .source(HUGGING_FACE)
                .domain("youngla.myshopify.com")
                .build();

        assertThatThrownBy(() -> service.resolve(datasetRow, profile))
                .isInstanceOf(MerchantEnrichmentException.class)
                .hasMessageContaining("no verified storefront identity");
    }

    @Test
    void plainDatasetRowsRetainTheGenericDomainFallback() {
        MerchantIdentityResolutionService service = new MerchantIdentityResolutionService(
                List.of(),
                new MerchantDomainNormalizer()
        );
        MerchantRaw datasetRow = MerchantRaw.builder()
                .source(HUGGING_FACE)
                .domain("WWW.Merchant.Example.")
                .build();

        var resolution = service.resolve(datasetRow, profile);

        assertThat(resolution.canonicalDomain()).isEqualTo("merchant.example");
        assertThat(resolution.claims()).hasSize(1);
        assertThat(resolution.claims().getFirst().role()).isEqualTo(STOREFRONT_DOMAIN);
    }
}
