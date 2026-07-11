package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.meant.api.module.user.repository.UserInventoryItemRepository;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UserCanonicalProductInventoryRankingTest {

    @Test
    void resolvesTheWholeCanonicalWindowWithOneInventoryQuery() {
        AtomicInteger reads = new AtomicInteger();
        UserInventoryItemRepository repository = (UserInventoryItemRepository) Proxy.newProxyInstance(
                UserInventoryItemRepository.class.getClassLoader(),
                new Class<?>[]{UserInventoryItemRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("findByUserIdOrderByUpdatedAtDesc")
                            && method.getParameterCount() == 1) {
                        reads.incrementAndGet();
                        return List.of();
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UserInventoryService service = new UserInventoryService(null, repository, null, null, null);

        var signals = service.canonicalRecommendationSignals(
                userId,
                List.of(product("a"), product("b"), product("c"))
        );

        assertThat(signals).containsOnlyKeys("a", "b", "c");
        assertThat(reads).hasValue(1);
    }

    private CanonicalProduct product(String key) {
        ProviderIdentity provider = new ProviderIdentity("FIXTURE");
        ExternalIdentifier merchant = new ExternalIdentifier(
                ExternalIdentifierType.MERCHANT, provider.value(), "merchant");
        ExternalIdentifier product = new ExternalIdentifier(
                ExternalIdentifierType.PRODUCT, provider.value(), key);
        ResultProvenance provenance = new ResultProvenance(
                provider,
                new DiscoverySourceIdentity(provider, ResultSourceType.PROVIDER_CATALOG, "fixture"),
                null,
                merchant,
                product,
                null,
                new ResultFreshness(Instant.parse("2026-07-11T10:00:00Z"), null),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "fixture", null)
        );
        Offer offer = new Offer(
                new OfferIdentity(
                        provider, OfferMerchantScope.external(merchant), product, null, List.of(), List.of(), null),
                "Merchant", null, null, null, null, List.of(), null, List.of(provenance));
        return new CanonicalProduct(
                key, "Product " + key, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(provenance), List.of(offer));
    }
}
