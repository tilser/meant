package com.meant.api.plugin.catalog.shopify;

import com.meant.api.plugin.catalog.common.dto.CatalogSourceFailure;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceFailureKind;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceOperation;
import com.meant.api.plugin.catalog.common.dto.CatalogSourcePage;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceResult;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.catalog.getproduct.CatalogGetProductCapability;
import com.meant.api.plugin.catalog.lookup.CatalogLookupCapability;
import com.meant.api.plugin.catalog.search.CatalogSearchCapability;
import com.meant.api.plugin.catalog.shopify.ShopifyGlobalCatalogNormalizer.NormalizedCandidates;
import com.meant.api.plugin.catalog.shopify.ShopifyGlobalCatalogResponseParser.ParsedResponse;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogArguments;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogArguments.Catalog;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogArguments.Pagination;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogGetProductRequest;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogLookupRequest;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogResponse;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogSearchRequest;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.transport.client.ShopifyUcpClient;
import com.meant.api.plugin.transport.client.ShopifyUcpTransportException;
import com.meant.api.plugin.transport.client.ShopifyUcpTransportFailure;
import com.meant.api.plugin.transport.dto.ShopifyUcpRequestOptions;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** One-source adapter. Federated invocation and ranking intentionally belong to PCOS-007. */
@Service
@Slf4j
public class ShopifyGlobalCatalogProvider {

    private final ShopifyUcpClient client;
    private final ShopifyGlobalCatalogResponseParser parser;
    private final ShopifyGlobalCatalogNormalizer normalizer;
    private final ShopifyGlobalCatalogCircuitBreaker circuitBreaker;
    private final ShopifyGlobalCatalogProperties properties;
    private final Semaphore concurrency;
    private final MeterRegistry meterRegistry;

    @Autowired
    public ShopifyGlobalCatalogProvider(
            ShopifyUcpClient client,
            ShopifyGlobalCatalogResponseParser parser,
            ShopifyGlobalCatalogNormalizer normalizer,
            ShopifyGlobalCatalogCircuitBreaker circuitBreaker,
            ShopifyGlobalCatalogProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.client = client;
        this.parser = parser;
        this.normalizer = normalizer;
        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
        this.concurrency = new Semaphore(properties.maximumConcurrentRequests());
        this.meterRegistry = meterRegistry;
    }

    ShopifyGlobalCatalogProvider(
            ShopifyUcpClient client,
            ShopifyGlobalCatalogResponseParser parser,
            ShopifyGlobalCatalogNormalizer normalizer,
            ShopifyGlobalCatalogCircuitBreaker circuitBreaker,
            ShopifyGlobalCatalogProperties properties
    ) {
        this(client, parser, normalizer, circuitBreaker, properties,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    /** Makes exactly one Global Catalog search call when the circuit permits an invocation. */
    public CatalogSourceResult searchCatalog(ShopifyGlobalCatalogSearchRequest request) {
        if (request == null || !hasText(request.query()) || invalidLimit(request.limit())) {
            return invalid(CatalogSourceOperation.SEARCH, "Shopify Global Catalog search request was invalid");
        }
        int limit = request.limit() == null
                ? properties.defaultResultLimit()
                : Math.min(request.limit(), properties.maximumResultLimit());
        ShopifyGlobalCatalogArguments arguments = new ShopifyGlobalCatalogArguments(new Catalog(
                request.query().trim(),
                null,
                null,
                null,
                null,
                request.context(),
                request.filters(),
                properties.view(),
                new Pagination(trimToNull(request.cursor()), limit)
        ));
        return execute(
                CatalogSourceOperation.SEARCH,
                CatalogSearchCapability.TOOL_NAME,
                CatalogSearchCapability.ID,
                arguments
        );
    }

    public CatalogSourceResult lookupCatalog(ShopifyGlobalCatalogLookupRequest request) {
        List<String> ids = request == null ? List.of() : distinctIds(request.ids());
        if (ids.isEmpty() || ids.size() > properties.maximumLookupIds()) {
            return invalid(CatalogSourceOperation.LOOKUP, "Shopify Global Catalog lookup identifiers were invalid");
        }
        ShopifyGlobalCatalogArguments arguments = new ShopifyGlobalCatalogArguments(new Catalog(
                null,
                ids,
                null,
                null,
                null,
                request.context(),
                request.filters(),
                properties.view(),
                null
        ));
        return execute(
                CatalogSourceOperation.LOOKUP,
                CatalogLookupCapability.TOOL_NAME,
                CatalogLookupCapability.ID,
                arguments
        );
    }

    public CatalogSourceResult getProduct(ShopifyGlobalCatalogGetProductRequest request) {
        if (request == null || !hasText(request.id())) {
            return invalid(CatalogSourceOperation.GET_PRODUCT, "Shopify Global Catalog product identifier was invalid");
        }
        ShopifyGlobalCatalogArguments arguments = new ShopifyGlobalCatalogArguments(new Catalog(
                null,
                null,
                request.id().trim(),
                request.selected(),
                cleanText(request.preferences()),
                request.context(),
                request.filters(),
                properties.view(),
                null
        ));
        return execute(
                CatalogSourceOperation.GET_PRODUCT,
                CatalogGetProductCapability.TOOL_NAME,
                CatalogGetProductCapability.ID,
                arguments
        );
    }

    private CatalogSourceResult execute(
            CatalogSourceOperation operation,
            String toolName,
            CapabilityId requiredCapability,
            ShopifyGlobalCatalogArguments arguments
    ) {
        long started = System.nanoTime();
        CatalogSourceResult result = executeUnmeasured(operation, toolName, requiredCapability, arguments);
        recordMetrics(operation, result, System.nanoTime() - started);
        return result;
    }

    private CatalogSourceResult executeUnmeasured(
            CatalogSourceOperation operation,
            String toolName,
            CapabilityId requiredCapability,
            ShopifyGlobalCatalogArguments arguments
    ) {
        if (!concurrency.tryAcquire()) {
            return failure(operation, new CatalogSourceFailure(
                    CatalogSourceFailureKind.UNAVAILABLE,
                    "Shopify Global Catalog request capacity is exhausted",
                    null,
                    null
            ));
        }
        try {
            if (!circuitBreaker.tryAcquire()) {
                return failure(operation, new CatalogSourceFailure(
                        CatalogSourceFailureKind.UNAVAILABLE,
                        "Shopify Global Catalog circuit is open",
                        properties.circuitOpenDuration(),
                        null
                ));
            }
            UcpToolResponse toolResponse = client.callTool(requestOptions(), toolName, arguments);
            ParsedResponse parsed = parser.parse(toolResponse, requiredCapability, operation);
            if ("error".equalsIgnoreCase(parsed.payload().ucp().status())) {
                circuitBreaker.recordSuccess();
                return new CatalogSourceResult(
                        ShopifyGlobalCatalogNormalizer.SHOPIFY,
                        discoverySource(),
                        operation,
                        parsed.payload().ucp().version(),
                        parsed.negotiatedCapabilities(),
                        List.of(),
                        page(parsed.payload()),
                        false,
                        new CatalogSourceFailure(
                                CatalogSourceFailureKind.INVALID_REQUEST,
                                "Shopify Global Catalog reported an application contract error",
                                null,
                                null
                        )
                );
            }
            NormalizedCandidates normalized = normalizer.normalize(parsed.payload());
            circuitBreaker.recordSuccess();
            return new CatalogSourceResult(
                    ShopifyGlobalCatalogNormalizer.SHOPIFY,
                    discoverySource(),
                    operation,
                    parsed.payload().ucp().version(),
                    parsed.negotiatedCapabilities(),
                    normalized.candidates(),
                    page(parsed.payload()),
                    normalized.truncated(),
                    null
            );
        } catch (ShopifyUcpTransportException exception) {
            CatalogSourceFailureKind kind = failureKind(exception.failure());
            if (breakerFailure(exception.failure())) {
                circuitBreaker.recordFailure(exception.retryAfter().orElse(null));
            } else {
                circuitBreaker.recordIgnoredFailure();
            }
            return failure(operation, new CatalogSourceFailure(
                    kind,
                    exception.getMessage(),
                    exception.retryAfter().orElse(null),
                    exception.upstreamStatus().orElse(null)
            ));
        } catch (ShopifyGlobalCatalogContractException | IllegalArgumentException exception) {
            circuitBreaker.recordFailure(null);
            return failure(operation, new CatalogSourceFailure(
                    CatalogSourceFailureKind.MALFORMED_RESPONSE,
                    "Shopify Global Catalog response could not be normalized safely",
                    null,
                    null
            ));
        } catch (RuntimeException exception) {
            circuitBreaker.recordFailure(null);
            log.warn(
                    "Shopify Global Catalog request failed unexpectedly; exceptionType={}",
                    exception.getClass().getName()
            );
            return failure(operation, new CatalogSourceFailure(
                    CatalogSourceFailureKind.TRANSIENT_UPSTREAM,
                    "Shopify Global Catalog request failed unexpectedly",
                    null,
                    null
            ));
        } finally {
            concurrency.release();
        }
    }

    private void recordMetrics(CatalogSourceOperation operation, CatalogSourceResult result, long elapsedNanos) {
        String outcome = result.successful() ? "success" : result.failure().kind().name().toLowerCase(Locale.ROOT);
        String operationTag = operation.name().toLowerCase(Locale.ROOT);
        Counter.builder("commerce.catalog.source.calls")
                .tag("provider", "shopify")
                .tag("source", properties.sourceIdentity())
                .tag("operation", operationTag)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
        Timer.builder("commerce.catalog.source.duration")
                .tag("provider", "shopify")
                .tag("source", properties.sourceIdentity())
                .tag("operation", operationTag)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(Duration.ofNanos(Math.max(0, elapsedNanos)));
        if (result.successful()) {
            DistributionSummary.builder("commerce.catalog.source.candidates")
                    .tag("provider", "shopify")
                    .tag("source", properties.sourceIdentity())
                    .tag("operation", operationTag)
                    .register(meterRegistry)
                    .record(result.candidates().size());
        }
    }

    private CatalogSourceResult invalid(CatalogSourceOperation operation, String message) {
        return failure(operation, new CatalogSourceFailure(
                CatalogSourceFailureKind.INVALID_REQUEST,
                message,
                null,
                null
        ));
    }

    private CatalogSourceResult failure(CatalogSourceOperation operation, CatalogSourceFailure failure) {
        return new CatalogSourceResult(
                ShopifyGlobalCatalogNormalizer.SHOPIFY,
                discoverySource(),
                operation,
                properties.protocolVersion(),
                NegotiatedCapabilities.none(),
                List.of(),
                null,
                false,
                failure
        );
    }

    private ShopifyUcpRequestOptions requestOptions() {
        return new ShopifyUcpRequestOptions(
                properties.endpoint(),
                properties.allowedHosts(),
                properties.requiredScopes(),
                properties.connectTimeout(),
                properties.readTimeout(),
                properties.requestDeadline()
        );
    }

    public DiscoverySourceIdentity discoverySourceIdentity() {
        return new DiscoverySourceIdentity(
                ShopifyGlobalCatalogNormalizer.SHOPIFY,
                ResultSourceType.PROVIDER_CATALOG,
                properties.sourceIdentity()
        );
    }

    private DiscoverySourceIdentity discoverySource() {
        return discoverySourceIdentity();
    }

    private CatalogSourcePage page(ShopifyGlobalCatalogResponse response) {
        return response.pagination() == null
                ? null
                : new CatalogSourcePage(
                        response.pagination().cursor(),
                        Boolean.TRUE.equals(response.pagination().hasNextPage()),
                        response.pagination().totalCount()
                );
    }

    private List<String> distinctIds(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String id : ids) {
            if (hasText(id)) {
                values.add(id.trim());
            }
        }
        return List.copyOf(values);
    }

    private List<String> cleanText(List<String> values) {
        return values == null
                ? null
                : values.stream().filter(this::hasText).map(String::trim).distinct().toList();
    }

    private boolean invalidLimit(Integer limit) {
        return limit != null && limit < 1;
    }

    private CatalogSourceFailureKind failureKind(ShopifyUcpTransportFailure failure) {
        return switch (failure) {
            case AUTHENTICATION -> CatalogSourceFailureKind.AUTHENTICATION;
            case INVALID_REQUEST -> CatalogSourceFailureKind.INVALID_REQUEST;
            case RATE_LIMITED -> CatalogSourceFailureKind.RATE_LIMITED;
            case TIMEOUT -> CatalogSourceFailureKind.TIMEOUT;
            case TRANSIENT_UPSTREAM -> CatalogSourceFailureKind.TRANSIENT_UPSTREAM;
            case MALFORMED_RESPONSE -> CatalogSourceFailureKind.MALFORMED_RESPONSE;
        };
    }

    private boolean breakerFailure(ShopifyUcpTransportFailure failure) {
        return switch (failure) {
            case RATE_LIMITED, TIMEOUT, TRANSIENT_UPSTREAM, MALFORMED_RESPONSE -> true;
            case AUTHENTICATION, INVALID_REQUEST -> false;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
