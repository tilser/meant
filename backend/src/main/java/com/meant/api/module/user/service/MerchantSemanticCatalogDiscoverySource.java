package com.meant.api.module.user.service;

import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.properties.MerchantMcpToolProperties;
import com.meant.api.module.merchant.service.MerchantCatalogDiscoveryEligibilityPolicy;
import com.meant.api.module.merchant.service.MerchantIntegrationLookupService;
import com.meant.api.module.merchant.service.MerchantSemanticProductSearchService;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductSearchResult;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsQuery;
import com.meant.api.module.merchant.service.query.SemanticProductSearchQuery;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRequest;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailure;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogSourceOperation;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.port.CatalogDiscoverySource;
import com.meant.api.module.catalog.service.CatalogDiscoverySourceMetrics;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Canonical adapter around the existing Meant merchant-semantic search and fan-out. */
@Component
public class MerchantSemanticCatalogDiscoverySource implements CatalogDiscoverySource {

    private static final ProviderIdentity PROVIDER = MerchantCatalogSourceIdentity.PROVIDER;
    private static final DiscoverySourceIdentity SOURCE = MerchantCatalogSourceIdentity.DISCOVERY_SOURCE;

    private final MerchantSemanticProductSearchService searchService;
    private final MerchantCatalogDiscoveryEligibilityPolicy eligibilityPolicy;
    private final MerchantIntegrationLookupService integrationLookupService;
    private final UserCanonicalProductCandidateMapper candidateMapper;
    private final UserProductSearchHashService hashService;
    private final CatalogDiscoverySourceMetrics metrics;
    private final Duration timeout;

    public MerchantSemanticCatalogDiscoverySource(
            MerchantSemanticProductSearchService searchService,
            MerchantCatalogDiscoveryEligibilityPolicy eligibilityPolicy,
            MerchantIntegrationLookupService integrationLookupService,
            UserCanonicalProductCandidateMapper candidateMapper,
            UserProductSearchHashService hashService,
            CatalogDiscoverySourceMetrics metrics,
            MerchantMcpToolProperties properties
    ) {
        this.searchService = searchService;
        this.eligibilityPolicy = eligibilityPolicy;
        this.integrationLookupService = integrationLookupService;
        this.candidateMapper = candidateMapper;
        this.hashService = hashService;
        this.metrics = metrics;
        this.timeout = Duration.ofMillis(properties.merchantTimeoutMilliseconds());
    }

    @Override
    public DiscoverySourceIdentity sourceIdentity() {
        return SOURCE;
    }

    @Override
    public Duration timeout() {
        return timeout;
    }

    @Override
    public CatalogSourceResult search(
            CatalogDiscoveryRequest request,
            Consumer<ProductCandidate> candidateConsumer
    ) {
        long startedNanos = System.nanoTime();
        CatalogSourceResult result = searchUnmeasured(request, candidateConsumer);
        metrics.record(result, System.nanoTime() - startedNanos);
        return result;
    }

    private CatalogSourceResult searchUnmeasured(
            CatalogDiscoveryRequest request,
            Consumer<ProductCandidate> candidateConsumer
    ) {
        Map<UUID, Optional<MerchantIntegrationResult>> integrations = new ConcurrentHashMap<>();
        Instant observedAt = Instant.now();
        try {
            MerchantSemanticProductSearchResult result = searchService.search(
                    query(request),
                    product -> candidate(product, integrations, observedAt).ifPresent(candidateConsumer),
                    merchants -> eligibilityPolicy.eligible(
                            merchants,
                            request.coveredProviders(),
                            request.merchantId()
                    )
            );
            List<ProductCandidate> candidates = safeProducts(result).stream()
                    .map(product -> candidate(product, integrations, observedAt).orElse(null))
                    .filter(Objects::nonNull)
                    .limit(request.candidateLimit())
                    .toList();
            boolean everyAttemptFailed = result != null
                    && result.merchants() != null
                    && !result.merchants().isEmpty()
                    && result.merchants().stream().allMatch(attempt -> attempt.error() != null);
            if (everyAttemptFailed) {
                return failure(CatalogSourceFailureKind.TRANSIENT_UPSTREAM,
                        "Merchant catalog discovery failed for every eligible merchant");
            }
            return new CatalogSourceResult(
                    PROVIDER,
                    SOURCE,
                    CatalogSourceOperation.SEARCH,
                    null,
                    NegotiatedCapabilities.none(),
                    candidates,
                    null,
                    candidates.size() >= request.candidateLimit(),
                    null
            );
        } catch (RuntimeException exception) {
            return failure(CatalogSourceFailureKind.TRANSIENT_UPSTREAM,
                    "Merchant catalog discovery failed unexpectedly");
        }
    }

    private SemanticProductSearchQuery query(CatalogDiscoveryRequest request) {
        return new SemanticProductSearchQuery(
                request.query(),
                request.merchantId(),
                null,
                null,
                null,
                request.candidateLimit(),
                request.context(),
                request.signals(),
                request.filters()
        );
    }

    private java.util.Optional<ProductCandidate> candidate(
            MerchantSemanticProductResult product,
            Map<UUID, Optional<MerchantIntegrationResult>> integrations,
            Instant observedAt
    ) {
        Optional<MerchantIntegrationResult> integration = integrations.computeIfAbsent(
                product.merchantId(),
                merchantId -> Optional.ofNullable(integration(product))
        );
        if (integration.isEmpty()) {
            return java.util.Optional.empty();
        }
        String productKey = hashService.productKey(product);
        return java.util.Optional.of(candidateMapper.from(product, productKey, integration.get(), observedAt));
    }

    private MerchantIntegrationResult integration(MerchantSemanticProductResult product) {
        List<MerchantIntegrationResult> integrations = integrationLookupService.listByMerchant(
                        new ListMerchantIntegrationsQuery(product.merchantId())
                ).stream()
                .filter(integration -> integration.provider() == MerchantIntegrationProvider.GENERIC_UCP)
                .filter(integration -> integration.status() == MerchantIntegrationStatus.ACTIVE)
                .filter(integration -> integration.roles().contains(MerchantIntegrationRole.STOREFRONT_CATALOG)
                        || integration.roles().contains(MerchantIntegrationRole.CATALOG_PROVENANCE))
                .toList();
        List<MerchantIntegrationResult> endpointMatches = integrations.stream()
                .filter(integration -> Objects.equals(integration.endpoint(), product.endpoint()))
                .toList();
        if (endpointMatches.size() == 1) {
            return endpointMatches.getFirst();
        }
        return endpointMatches.isEmpty() && integrations.size() == 1 ? integrations.getFirst() : null;
    }

    private List<MerchantSemanticProductResult> safeProducts(MerchantSemanticProductSearchResult result) {
        return result == null || result.products() == null ? List.of() : result.products();
    }

    private CatalogSourceResult failure(CatalogSourceFailureKind kind, String message) {
        return new CatalogSourceResult(
                PROVIDER,
                SOURCE,
                CatalogSourceOperation.SEARCH,
                null,
                NegotiatedCapabilities.none(),
                List.of(),
                null,
                false,
                new CatalogSourceFailure(kind, message, null, null)
        );
    }
}
