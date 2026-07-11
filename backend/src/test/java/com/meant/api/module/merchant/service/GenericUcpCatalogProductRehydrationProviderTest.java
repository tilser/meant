package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.query.GetMerchantProductDetailsQuery;
import com.meant.api.plugin.catalog.common.dto.CatalogProductReference;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationContext;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationStatus;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.service.GenericUcpCatalogDataUsePolicy;
import com.meant.api.plugin.catalog.common.service.GenericUcpCatalogDataUseProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class GenericUcpCatalogProductRehydrationProviderTest {
    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");

    @Test
    void rehydratesServerResolvedReferenceThroughProductDetailsOutsideTransaction() {
        FakeProductDetailsService detailsService = new FakeProductDetailsService();
        GenericUcpCatalogProductRehydrationProvider provider = new GenericUcpCatalogProductRehydrationProvider(
                detailsService,
                new GenericUcpCatalogDataUseProperties(
                        Duration.ofHours(24),
                        Duration.ofMinutes(2),
                        Duration.ofDays(30)
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        CatalogProductReference reference = reference();

        var result = provider.rehydrate(
                List.of(reference),
                new CatalogRehydrationContext("CZ", "en")
        ).getFirst();

        assertThat(detailsService.query.productId()).isEqualTo("product-1");
        assertThat(detailsService.query.merchantId()).isEqualTo(reference.localMerchantId());
        assertThat(detailsService.transactionActive).isFalse();
        assertThat(result.status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.facts().price().minorUnits()).isEqualTo(1299);
        assertThat(result.facts().selectedVariant().value()).isEqualTo("variant-1");
        assertThat(result.facts().freshness().freshUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
    }

    private CatalogProductReference reference() {
        return new CatalogProductReference(
                "saved-1",
                GenericUcpCatalogDataUsePolicy.SOURCE,
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                null,
                new ExternalIdentifier(ExternalIdentifierType.MERCHANT, "GENERIC_UCP", "merchant-1"),
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "GENERIC_UCP", "product-1"),
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "GENERIC_UCP", "variant-1"),
                List.of()
        );
    }

    private static class FakeProductDetailsService extends MerchantProductDetailsService {
        private GetMerchantProductDetailsQuery query;
        private boolean transactionActive;

        FakeProductDetailsService() {
            super(null, null);
        }

        @Override
        public ProductDetailsResult get(GetMerchantProductDetailsQuery query) {
            this.query = query;
            this.transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            return new ProductDetailsResult("https://merchant.test/mcp", "redacted", new ProductDetailsResponse.Product(
                    "product-1",
                    "Current product",
                    "Current description",
                    "https://merchant.test/products/1",
                    "https://merchant.test/media/1.jpg",
                    List.of(),
                    List.of(),
                    1,
                    new ProductDetailsResponse.PriceRange("12.99", "12.99", "USD"),
                    false,
                    List.of(),
                    new ProductDetailsResponse.SelectedVariant(
                            "variant-1",
                            "Default",
                            "12.99",
                            "USD",
                            "https://merchant.test/media/1.jpg",
                            "Current product",
                            true,
                            List.of(new ProductDetailsResponse.SelectedOption("Size", "M"))
                    )
            ));
        }
    }
}
