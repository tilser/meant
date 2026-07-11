package com.meant.api.module.catalog.service;

import com.meant.api.module.catalog.service.port.CatalogProductRehydrationProvider;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CatalogProductRehydrationServiceTest {

    @Test
    void batchesOneResultPageAndReturnsFreshTypedFactsWithoutNPlusOneDispatch() {
        AtomicInteger calls = new AtomicInteger();
        CatalogProductRehydrationProvider provider = provider(calls, false);
        CatalogProductRehydrationService service = service(List.of(provider));
        List<CatalogProductReference> references = java.util.stream.IntStream.range(0, 50)
                .mapToObj(index -> reference("saved-" + index))
                .toList();

        List<CatalogProductRehydrationResult> results = service.rehydrate(
                references,
                new CatalogRehydrationContext("CZ", "en")
        );

        assertThat(calls).hasValue(1);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo(CatalogRehydrationStatus.FRESH);
            assertThat(result.facts()).isNotNull();
            assertThat(result.failure()).isNull();
        });
    }

    @Test
    void unsupportedAndDegradedResultsNeverFallBackToStaleAuthoritativeFacts() {
        CatalogProductReference reference = reference("saved-1");
        CatalogProductRehydrationResult unsupported = service(List.of()).rehydrate(
                reference,
                new CatalogRehydrationContext(null, null)
        );
        CatalogProductRehydrationResult degraded = service(List.of(provider(new AtomicInteger(), true))).rehydrate(
                reference,
                new CatalogRehydrationContext(null, null)
        );

        assertThat(unsupported.status()).isEqualTo(CatalogRehydrationStatus.UNSUPPORTED);
        assertThat(unsupported.facts()).isNull();
        assertThat(degraded.status()).isEqualTo(CatalogRehydrationStatus.DEGRADED);
        assertThat(degraded.facts()).isNull();
    }

    private CatalogProductRehydrationService service(List<CatalogProductRehydrationProvider> providers) {
        return new CatalogProductRehydrationService(
                providers,
                new CatalogProductRehydrationMetrics(new SimpleMeterRegistry())
        );
    }

    private CatalogProductRehydrationProvider provider(AtomicInteger calls, boolean fail) {
        return new CatalogProductRehydrationProvider() {
            @Override
            public boolean supports(com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity source) {
                return MerchantCatalogSourceIdentity.DISCOVERY_SOURCE.equals(source);
            }

            @Override
            public List<CatalogProductRehydrationResult> rehydrate(
                    List<CatalogProductReference> references,
                    CatalogRehydrationContext context
            ) {
                calls.incrementAndGet();
                if (fail) {
                    throw new IllegalStateException("controlled fake failure");
                }
                ResultFreshness freshness = new ResultFreshness(
                        Instant.parse("2026-07-11T00:00:00Z"),
                        Instant.parse("2026-07-11T00:02:00Z")
                );
                return references.stream()
                        .map(reference -> CatalogProductRehydrationResult.fresh(reference, reference,
                                new RehydratedCommercialFacts(
                                        "Fresh product",
                                        null,
                                        OfferAvailability.unknown(),
                                        reference.externalVariantReference(),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        freshness,
                                        CommercialFactsFreshness.fromSingleObservation(freshness)
                                )))
                        .toList();
            }
        };
    }

    private CatalogProductReference reference(String key) {
        return new CatalogProductReference(
                key,
                MerchantCatalogSourceIdentity.DISCOVERY_SOURCE,
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000010"),
                null,
                new ExternalIdentifier(ExternalIdentifierType.MERCHANT, "GENERIC_UCP", "merchant-1"),
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "GENERIC_UCP", "product-1"),
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "GENERIC_UCP", "variant-1"),
                List.of()
        );
    }
}
