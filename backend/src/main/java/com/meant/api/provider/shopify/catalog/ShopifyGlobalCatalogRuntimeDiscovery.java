package com.meant.api.provider.shopify.catalog;

import com.meant.api.module.catalog.service.port.CatalogSearchParameterContractProvider;
import com.meant.api.plugin.transport.client.UcpMcpClient;
import com.meant.api.plugin.transport.client.UcpMcpException;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Resolves and validates Shopify's live UCP catalog route.
 *
 * <p>Only a complete profile + {@code tools/list} validation becomes last-known-good. Network
 * failures can reuse that snapshot; an explicit incompatible document always fails closed.</p>
 */
@Component
@Slf4j
public class ShopifyGlobalCatalogRuntimeDiscovery implements CatalogSearchParameterContractProvider {

    private static final String SHOPPING_SERVICE = "dev.ucp.shopping";
    private static final String SEARCH_CAPABILITY = "dev.ucp.shopping.catalog.search";
    private static final String GLOBAL_CAPABILITY = "dev.shopify.catalog.global";
    private static final String SEARCH_TOOL = "search_catalog";

    private static final Map<String, String> REQUIRED_SEARCH_SCHEMA_PATHS = requiredSearchSchemaPaths();
    private static final Map<String, String> REQUIRED_SEARCH_SCHEMA_TYPES = requiredSearchSchemaTypes();

    private static final String SEARCH_PARAMETER_CONTRACT_BODY = """
            Populate every relevant constraint that is grounded in trusted evidence. Do not try to fill every
            optional field and do not ask for irrelevant refinements.

            Shopify/UCP search_catalog parameters:
            - query; like item references; view; and cursor/limit pagination.
            - context: address country, address region, postal code, language, currency, and intent.
            - observed platform signals: buyer IP and user agent. These are server-provided environment data, never
              facts to ask the buyer for or infer from conversation.
            - filters.available.
            - filters.condition: NEW or SECONDHAND.
            - filters.ships_to: ISO country plus optional region and postal code.
            - filters.ships_from: one or more ISO countries. Origin region and postal code are not supported as hard
              fields. Preserve buyer-stated origin region/postal wording in effectiveQuery so it remains a soft
              relevance constraint.
            - filters.price: optional minimum and/or maximum minor-unit amount.
            - filters.shops: trusted Shopify shop GIDs.
            - filters.categories: trusted taxonomy identifiers.
            - filters.attributes: exactly Color, Size, and Target gender.
            - filters.rating: optional minimum rating and/or minimum rating count.
            - filters.price_tier: LOW, MEDIUM, or HIGH.

            Meant policy fixes AVAILABLE=true. Emit PRICE only when trusted buyer evidence explicitly says USD,
            US dollars, or U.S. dollars; a bare number, "$", generic "dollars", or an unverified profile budget is
            not USD evidence. A verified PRICE is paired downstream with USD context. SHOPS and CATEGORIES require
            trusted IDs, but no buyer-text ID resolver is available, so never emit or request them. Keep brand,
            merchant, category, style, material, use-case, and other soft terms in effectiveQuery/intent. Fit is not
            a supported search filter. Size values are opaque strings: preserve the user's exact size string and never
            infer a size system or convert units. Explicit filter-specific indifference is persisted as ANY; ANY
            deliberately omits that provider filter.
            """;

    private final RestClient restClient;
    private final UcpMcpClient ucpMcpClient;
    private final ShopifyGlobalCatalogProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ExecutorService executor;
    private final Object refreshMonitor = new Object();

    private volatile VerifiedSnapshot lastKnownGood;
    private volatile ResolvedRoute cachedColdFallback;
    private volatile Instant refreshAfter = Instant.MIN;

    @Autowired
    public ShopifyGlobalCatalogRuntimeDiscovery(
            RestClient.Builder restClientBuilder,
            UcpMcpClient ucpMcpClient,
            ShopifyGlobalCatalogProperties properties,
            ObjectMapper objectMapper
    ) {
        this(
                discoveryRestClient(restClientBuilder, properties),
                ucpMcpClient,
                properties,
                objectMapper,
                Clock.systemUTC(),
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    ShopifyGlobalCatalogRuntimeDiscovery(
            RestClient restClient,
            UcpMcpClient ucpMcpClient,
            ShopifyGlobalCatalogProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this(
                restClient,
                ucpMcpClient,
                properties,
                objectMapper,
                clock,
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    ShopifyGlobalCatalogRuntimeDiscovery(
            RestClient restClient,
            UcpMcpClient ucpMcpClient,
            ShopifyGlobalCatalogProperties properties,
            ObjectMapper objectMapper,
            Clock clock,
            ExecutorService executor
    ) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.ucpMcpClient = Objects.requireNonNull(ucpMcpClient, "ucpMcpClient");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Returns the safe route for provider calls.
     *
     * <p>When the source or live runtime discovery is disabled, or a transient cold-start fetch
     * fails, the already configuration-validated endpoint remains available. Explicit
     * incompatibility never falls back.</p>
     */
    public URI endpoint() {
        return resolveRoute().endpoint();
    }

    @Override
    public String currentSearchParameterContract() {
        ResolvedRoute route = resolveRoute();
        String status = route.runtimeVerified()
                ? "Runtime-verified against the current Shopify profile and search_catalog input schema.\n\n"
                : "Configured, locally validated cold-start baseline. Live Shopify discovery is temporarily "
                        + "unavailable, so do not assume parameters beyond this baseline.\n\n";
        return status + SEARCH_PARAMETER_CONTRACT_BODY;
    }

    ResolvedRoute resolveRoute() {
        if (!properties.discoveryEnabled() || !properties.runtimeDiscoveryEnabled()) {
            return ResolvedRoute.configured(
                    safeEndpoint(properties.endpoint(), "Configured Shopify Global Catalog endpoint")
            );
        }

        Instant now = clock.instant();
        VerifiedSnapshot current = lastKnownGood;
        if (current != null && now.isBefore(refreshAfter)) {
            return current.route();
        }
        ResolvedRoute fallback = cachedColdFallback;
        if (current == null && fallback != null && now.isBefore(refreshAfter)) {
            return fallback;
        }

        synchronized (refreshMonitor) {
            now = clock.instant();
            current = lastKnownGood;
            if (current != null && now.isBefore(refreshAfter)) {
                return current.route();
            }
            fallback = cachedColdFallback;
            if (current == null && fallback != null && now.isBefore(refreshAfter)) {
                return fallback;
            }
            try {
                VerifiedSnapshot refreshed = discoverWithinDeadline(now);
                lastKnownGood = refreshed;
                cachedColdFallback = null;
                refreshAfter = now.plus(properties.discoveryCacheTtl());
                return refreshed.route();
            } catch (ShopifyGlobalCatalogDiscoveryException exception) {
                if (exception.incompatible()) {
                    throw exception;
                }
                if (current != null) {
                    refreshAfter = now.plus(properties.discoveryCacheTtl());
                    log.warn(
                            "Shopify Global Catalog discovery refresh failed transiently; using last-known-good route"
                    );
                    return current.route();
                }
                log.warn(
                        "Shopify Global Catalog discovery failed transiently during cold start; "
                                + "using the configured endpoint"
                );
                ResolvedRoute configured = ResolvedRoute.configured(
                        safeEndpoint(properties.endpoint(), "Configured Shopify Global Catalog endpoint")
                );
                cachedColdFallback = configured;
                refreshAfter = now.plus(properties.discoveryCacheTtl());
                return configured;
            }
        }
    }

    private VerifiedSnapshot discoverWithinDeadline(Instant verifiedAt) {
        Future<VerifiedSnapshot> discovery = executor.submit(() -> discover(verifiedAt));
        try {
            return discovery.get(properties.requestDeadline().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            discovery.cancel(true);
            throw transientFailure(
                    "Shopify Global Catalog discovery exceeded its overall deadline",
                    exception
            );
        } catch (InterruptedException exception) {
            discovery.cancel(true);
            Thread.currentThread().interrupt();
            throw transientFailure("Shopify Global Catalog discovery was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ShopifyGlobalCatalogDiscoveryException discoveryException) {
                throw discoveryException;
            }
            throw transientFailure("Shopify Global Catalog discovery failed unexpectedly", cause);
        }
    }

    private VerifiedSnapshot discover(Instant verifiedAt) {
        URI rootProfileUri = profileUri(properties.endpoint());
        JsonNode rootProfile = fetchProfile(rootProfileUri);
        JsonNode selectedProfile = selectProtocolProfile(rootProfile, rootProfileUri);
        JsonNode ucp = requireObject(selectedProfile, "ucp", "Shopify UCP profile");

        requireVersion(ucp, properties.protocolVersion(), "Shopify UCP profile");
        requireCapability(ucp, SEARCH_CAPABILITY, false);
        requireCapability(ucp, GLOBAL_CAPABILITY, true);
        URI endpoint = shoppingMcpEndpoint(ucp);

        JsonNode tools = listTools(endpoint);
        validateSearchTool(tools);
        return new VerifiedSnapshot(new ResolvedRoute(endpoint, true), verifiedAt);
    }

    private JsonNode selectProtocolProfile(JsonNode rootProfile, URI rootProfileUri) {
        JsonNode rootUcp = requireObject(rootProfile, "ucp", "Shopify root UCP profile");
        String advertisedVersion = requiredText(rootUcp, "version", "Shopify root UCP profile");
        if (properties.protocolVersion().equals(advertisedVersion)) {
            return rootProfile;
        }

        JsonNode supportedVersions = rootUcp.get("supported_versions");
        String selectedUri = supportedVersions != null && supportedVersions.isObject()
                ? scalarText(supportedVersions.get(properties.protocolVersion()))
                : null;
        if (selectedUri == null) {
            throw incompatible(
                    "Shopify UCP profile does not advertise configured protocol version "
                            + properties.protocolVersion()
            );
        }
        URI versionProfileUri = safeUri(selectedUri, "Shopify version-specific UCP profile");
        if (versionProfileUri.equals(rootProfileUri)) {
            throw incompatible("Shopify version-specific UCP profile points back to an incompatible root profile");
        }
        JsonNode versionProfile = fetchProfile(versionProfileUri);
        JsonNode versionUcp = requireObject(versionProfile, "ucp", "Shopify version-specific UCP profile");
        requireVersion(versionUcp, properties.protocolVersion(), "Shopify version-specific UCP profile");
        return versionProfile;
    }

    private JsonNode fetchProfile(URI profileUri) {
        safeEndpoint(profileUri, "Shopify UCP profile");
        try {
            JsonNode profile = restClient.get()
                    .uri(profileUri)
                    .retrieve()
                    .body(JsonNode.class);
            if (profile == null || !profile.isObject()) {
                throw incompatible("Shopify UCP profile was not a JSON object");
            }
            return profile;
        } catch (ShopifyGlobalCatalogDiscoveryException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw httpFailure("Shopify UCP profile fetch", exception);
        } catch (HttpMessageConversionException exception) {
            throw ShopifyGlobalCatalogDiscoveryException.incompatible(
                    "Shopify UCP profile was malformed JSON", exception);
        } catch (ResourceAccessException exception) {
            throw transientFailure("Shopify UCP profile could not be reached", exception);
        } catch (RestClientException exception) {
            throw transientFailure("Shopify UCP profile fetch failed", exception);
        }
    }

    private JsonNode listTools(URI endpoint) {
        try {
            String result = ucpMcpClient.listTools(restClient, endpoint);
            JsonNode tools = objectMapper.readTree(result);
            if (tools == null || !tools.isObject()) {
                throw incompatible("Shopify MCP tools/list result was not a JSON object");
            }
            return tools;
        } catch (ShopifyGlobalCatalogDiscoveryException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw httpFailure("Shopify MCP tools/list", exception);
        } catch (ResourceAccessException exception) {
            throw transientFailure("Shopify MCP tools/list could not be reached", exception);
        } catch (JacksonException | UcpMcpException exception) {
            throw ShopifyGlobalCatalogDiscoveryException.incompatible(
                    "Shopify MCP tools/list returned an incompatible response", exception);
        } catch (RestClientException exception) {
            throw transientFailure("Shopify MCP tools/list failed", exception);
        }
    }

    private void requireVersion(JsonNode ucp, String expected, String context) {
        String version = requiredText(ucp, "version", context);
        if (!expected.equals(version)) {
            throw incompatible(context + " advertises unsupported protocol version " + version);
        }
    }

    private void requireCapability(JsonNode ucp, String capabilityName, boolean requireSearchParent) {
        JsonNode capabilities = requireObject(ucp, "capabilities", "Shopify UCP profile");
        JsonNode advertisements = capabilities.get(capabilityName);
        if (advertisements == null || !advertisements.isArray() || advertisements.isEmpty()) {
            throw incompatible("Shopify UCP profile does not advertise " + capabilityName);
        }
        boolean compatible = false;
        for (JsonNode advertisement : advertisements.values()) {
            if (!advertisement.isObject()
                    || !properties.protocolVersion().equals(scalarText(advertisement.get("version")))) {
                continue;
            }
            if (requireSearchParent && !containsText(advertisement.get("extends"), SEARCH_CAPABILITY)) {
                continue;
            }
            compatible = true;
            break;
        }
        if (!compatible) {
            throw incompatible(
                    "Shopify UCP profile has no compatible " + capabilityName + " advertisement"
            );
        }
    }

    private URI shoppingMcpEndpoint(JsonNode ucp) {
        JsonNode services = requireObject(ucp, "services", "Shopify UCP profile");
        JsonNode advertisements = services.get(SHOPPING_SERVICE);
        if (advertisements == null || !advertisements.isArray()) {
            throw incompatible("Shopify UCP profile does not advertise the shopping service");
        }
        URI selected = null;
        for (JsonNode advertisement : advertisements.values()) {
            if (!advertisement.isObject()
                    || !"mcp".equalsIgnoreCase(scalarText(advertisement.get("transport")))
                    || !properties.protocolVersion().equals(scalarText(advertisement.get("version")))) {
                continue;
            }
            String endpointText = scalarText(advertisement.get("endpoint"));
            if (endpointText == null) {
                throw incompatible("Shopify shopping MCP service omitted its endpoint");
            }
            URI candidate = safeUri(endpointText, "Shopify shopping MCP endpoint");
            if (selected != null && !selected.equals(candidate)) {
                throw incompatible("Shopify UCP profile advertises ambiguous shopping MCP endpoints");
            }
            selected = candidate;
        }
        if (selected == null) {
            throw incompatible("Shopify UCP profile has no compatible shopping MCP endpoint");
        }
        return selected;
    }

    private void validateSearchTool(JsonNode toolsResult) {
        JsonNode tools = toolsResult.get("tools");
        if (tools == null || !tools.isArray()) {
            throw incompatible("Shopify MCP tools/list omitted tools");
        }
        JsonNode inputSchema = null;
        for (JsonNode tool : tools.values()) {
            if (tool.isObject() && SEARCH_TOOL.equals(scalarText(tool.get("name")))) {
                if (inputSchema != null) {
                    throw incompatible("Shopify MCP tools/list advertised search_catalog more than once");
                }
                inputSchema = tool.get("inputSchema");
            }
        }
        if (inputSchema == null || !inputSchema.isObject()) {
            throw incompatible("Shopify MCP tools/list omitted search_catalog inputSchema");
        }

        for (Map.Entry<String, String> required : REQUIRED_SEARCH_SCHEMA_PATHS.entrySet()) {
            JsonNode field = inputSchema.at(required.getValue());
            if (field == null || field.isMissingNode() || field.isNull()) {
                throw incompatible(
                        "Shopify search_catalog inputSchema omitted supported path " + required.getKey()
                );
            }
            requireType(field, REQUIRED_SEARCH_SCHEMA_TYPES.get(required.getKey()), required.getKey());
        }
        requireArrayContains(inputSchema, "/required", "meta", "search_catalog");
        requireArrayContains(inputSchema, "/required", "catalog", "search_catalog");
        requireArrayContains(
                inputSchema,
                "/properties/meta/required",
                "ucp-agent",
                "search_catalog.meta"
        );
        requireArrayContains(
                inputSchema,
                "/properties/meta/properties/ucp-agent/required",
                "profile",
                "search_catalog.meta.ucp-agent"
        );
        requireNoUnsupportedRequired(
                inputSchema,
                "/required",
                Set.of("meta", "catalog"),
                "search_catalog"
        );
        requireNoUnsupportedRequired(
                inputSchema,
                "/properties/meta/required",
                Set.of("ucp-agent"),
                "search_catalog.meta"
        );
        requireNoUnsupportedRequired(
                inputSchema,
                "/properties/meta/properties/ucp-agent/required",
                Set.of("profile"),
                "search_catalog.meta.ucp-agent"
        );
        requireNoUnsupportedRequired(
                inputSchema,
                "/properties/catalog/required",
                Set.of("view", "pagination"),
                "search_catalog.catalog"
        );
        requireNoUnsupportedRequired(
                inputSchema,
                "/properties/catalog/properties/context/required",
                Set.of(),
                "search_catalog.catalog.context"
        );
        requireNoUnsupportedRequired(
                inputSchema,
                "/properties/catalog/properties/signals/required",
                Set.of(),
                "search_catalog.catalog.signals"
        );
        requireNoUnsupportedRequired(
                inputSchema,
                "/properties/catalog/properties/filters/required",
                Set.of(),
                "search_catalog.catalog.filters"
        );
        requireNoUnsupportedRequired(
                inputSchema,
                "/properties/catalog/properties/filters/properties/price/required",
                Set.of(),
                "search_catalog.catalog.filters.price"
        );
        requireNoUnsupportedRequired(
                inputSchema,
                "/properties/catalog/properties/filters/properties/rating/required",
                Set.of("variant"),
                "search_catalog.catalog.filters.rating"
        );
        requireNoUnsupportedRequired(
                inputSchema,
                "/properties/catalog/properties/filters/properties/rating/properties/variant/required",
                Set.of(),
                "search_catalog.catalog.filters.rating.variant"
        );
        requireNoUnsupportedRequired(
                inputSchema,
                "/properties/catalog/properties/pagination/required",
                Set.of("limit"),
                "search_catalog.catalog.pagination"
        );
        requireArrayContains(
                inputSchema,
                "/properties/catalog/properties/filters/properties/ships_to/required",
                "country",
                "search_catalog.catalog.filters.ships_to"
        );
        requireArrayContains(
                inputSchema,
                "/properties/catalog/properties/filters/properties/ships_from/items/required",
                "country",
                "search_catalog.catalog.filters.ships_from items"
        );
        requireArrayContains(
                inputSchema,
                "/properties/catalog/properties/filters/properties/attributes/items/required",
                "name",
                "search_catalog.catalog.filters.attributes items"
        );
        requireArrayContains(
                inputSchema,
                "/properties/catalog/properties/filters/properties/attributes/items/required",
                "values",
                "search_catalog.catalog.filters.attributes items"
        );
        validateLikeItemReference(inputSchema);
        requireNoUnsupportedRequired(
                inputSchema,
                "/properties/catalog/properties/filters/properties/ships_to/required",
                Set.of("country"),
                "search_catalog.catalog.filters.ships_to"
        );
        requireNoUnsupportedRequired(
                inputSchema,
                "/properties/catalog/properties/filters/properties/ships_from/items/required",
                Set.of("country"),
                "search_catalog.catalog.filters.ships_from items"
        );
        requireNoUnsupportedRequired(
                inputSchema,
                "/properties/catalog/properties/filters/properties/attributes/items/required",
                Set.of("name", "values"),
                "search_catalog.catalog.filters.attributes items"
        );
        requireVocabulary(
                inputSchema.at("/properties/catalog/properties/filters/properties/condition"),
                Set.of("new", "secondhand"),
                "catalog.filters.condition"
        );
        requireVocabulary(
                inputSchema.at("/properties/catalog/properties/filters/properties/price_tier"),
                Set.of("low", "medium", "high"),
                "catalog.filters.price_tier"
        );
        requireVocabulary(
                inputSchema.at(
                        "/properties/catalog/properties/filters/properties/attributes/items/properties/name"),
                Set.of("color", "size", "target gender"),
                "catalog.filters.attributes[].name"
        );
    }

    private void requireType(JsonNode schema, String expectedType, String context) {
        if (expectedType == null || !expectedType.equals(scalarText(schema.get("type")))) {
            throw incompatible(
                    "Shopify search_catalog inputSchema changed the type of " + context
            );
        }
    }

    private void validateLikeItemReference(JsonNode inputSchema) {
        JsonNode items = inputSchema.at("/properties/catalog/properties/like/items");
        JsonNode variants = items == null ? null : items.get("oneOf");
        if (variants == null || !variants.isArray()) {
            variants = objectMapper.createArrayNode().add(items);
        }
        for (JsonNode variant : variants.values()) {
            JsonNode id = variant == null ? null : variant.at("/properties/id");
            if (id != null
                    && !id.isMissingNode()
                    && "string".equals(scalarText(id.get("type")))
                    && containsText(variant.get("required"), "id")) {
                requireNoUnsupportedRequired(
                        variant,
                        "/required",
                        Set.of("id"),
                        "search_catalog.catalog.like item reference"
                );
                return;
            }
        }
        throw incompatible(
                "Shopify search_catalog inputSchema omitted a compatible like item-reference variant"
        );
    }

    private void requireVocabulary(JsonNode schema, Set<String> expectedValues, String context) {
        Set<String> advertised = new java.util.HashSet<>();
        collectEnumValues(schema == null ? null : schema.get("enum"), advertised);
        collectEnumValues(
                schema == null || schema.get("items") == null ? null : schema.get("items").get("enum"),
                advertised
        );
        String description = scalarText(schema == null ? null : schema.get("description"));
        String normalizedDescription = description == null ? "" : description.toLowerCase(Locale.ROOT);
        boolean supported = expectedValues.stream().allMatch(expected ->
                advertised.contains(expected) || normalizedDescription.contains(expected));
        if (!supported) {
            throw incompatible(
                    "Shopify search_catalog inputSchema no longer advertises the supported vocabulary for "
                            + context
            );
        }
    }

    private void collectEnumValues(JsonNode values, Set<String> target) {
        if (values == null || !values.isArray()) {
            return;
        }
        for (JsonNode value : values.values()) {
            String text = scalarText(value);
            if (text != null) {
                target.add(text.toLowerCase(Locale.ROOT));
            }
        }
    }

    private void requireArrayContains(JsonNode root, String pointer, String expected, String context) {
        JsonNode values = root.at(pointer);
        if (values == null || !values.isArray() || !containsText(values, expected)) {
            throw incompatible(context + " does not require " + expected);
        }
    }

    private void requireNoUnsupportedRequired(
            JsonNode root,
            String pointer,
            Set<String> supported,
            String context
    ) {
        JsonNode values = root.at(pointer);
        if (values == null || values.isMissingNode()) {
            return;
        }
        if (!values.isArray()) {
            throw incompatible(context + " has a non-array required-field declaration");
        }
        for (JsonNode value : values.values()) {
            String required = scalarText(value);
            if (required == null || !supported.contains(required)) {
                throw incompatible(context + " added an unsupported required field");
            }
        }
    }

    private JsonNode requireObject(JsonNode parent, String field, String context) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isObject()) {
            throw incompatible(context + " omitted object " + field);
        }
        return value;
    }

    private String requiredText(JsonNode parent, String field, String context) {
        String value = scalarText(parent == null ? null : parent.get(field));
        if (value == null) {
            throw incompatible(context + " omitted " + field);
        }
        return value;
    }

    private boolean containsText(JsonNode values, String expected) {
        if (values == null) {
            return false;
        }
        if (values.isArray()) {
            for (JsonNode value : values.values()) {
                if (expected.equals(scalarText(value))) {
                    return true;
                }
            }
            return false;
        }
        return expected.equals(scalarText(values));
    }

    private URI profileUri(URI configuredEndpoint) {
        try {
            return new URI(
                    "https",
                    null,
                    configuredEndpoint.getHost(),
                    configuredEndpoint.getPort(),
                    "/.well-known/ucp",
                    null,
                    null
            );
        } catch (URISyntaxException exception) {
            throw ShopifyGlobalCatalogDiscoveryException.incompatible(
                    "Configured Shopify host could not form a UCP profile URI", exception);
        }
    }

    private URI safeUri(String value, String context) {
        try {
            return safeEndpoint(URI.create(value), context);
        } catch (IllegalArgumentException exception) {
            throw ShopifyGlobalCatalogDiscoveryException.incompatible(
                    context + " was not a valid URI", exception);
        }
    }

    private URI safeEndpoint(URI endpoint, String context) {
        if (endpoint == null
                || !endpoint.isAbsolute()
                || !"https".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getFragment() != null
                || !allowedHosts().contains(endpoint.getHost().toLowerCase(Locale.ROOT))) {
            throw incompatible(context + " must use an allowlisted HTTPS host");
        }
        return endpoint;
    }

    private Set<String> allowedHosts() {
        return properties.allowedHosts().stream()
                .map(host -> host.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private ShopifyGlobalCatalogDiscoveryException httpFailure(
            String context,
            RestClientResponseException exception
    ) {
        HttpStatusCode status = exception.getStatusCode();
        if (status.is5xxServerError()
                || status.value() == 408
                || status.value() == 425
                || status.value() == 429) {
            return transientFailure(context + " was temporarily unavailable", exception);
        }
        return ShopifyGlobalCatalogDiscoveryException.incompatible(
                context + " was rejected with HTTP " + status.value(), exception);
    }

    private ShopifyGlobalCatalogDiscoveryException incompatible(String message) {
        return ShopifyGlobalCatalogDiscoveryException.incompatible(message);
    }

    private ShopifyGlobalCatalogDiscoveryException transientFailure(String message, Throwable cause) {
        return ShopifyGlobalCatalogDiscoveryException.transientFailure(message, cause);
    }

    private String scalarText(JsonNode value) {
        if (value == null
                || value.isNull()
                || value.isMissingNode()
                || value.isObject()
                || value.isArray()) {
            return null;
        }
        String text = value.asString().trim();
        return text.isEmpty() ? null : text;
    }

    private static RestClient discoveryRestClient(
            RestClient.Builder builder,
            ShopifyGlobalCatalogProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.clone().requestFactory(requestFactory).build();
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }

    private static Map<String, String> requiredSearchSchemaPaths() {
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("meta", "/properties/meta");
        paths.put("meta.ucp-agent", "/properties/meta/properties/ucp-agent");
        paths.put("meta.ucp-agent.profile",
                "/properties/meta/properties/ucp-agent/properties/profile");
        paths.put("catalog", "/properties/catalog");
        paths.put("catalog.query", "/properties/catalog/properties/query");
        paths.put("catalog.like", "/properties/catalog/properties/like");
        paths.put("catalog.context", "/properties/catalog/properties/context");
        paths.put("catalog.context.address_country",
                "/properties/catalog/properties/context/properties/address_country");
        paths.put("catalog.context.address_region",
                "/properties/catalog/properties/context/properties/address_region");
        paths.put("catalog.context.postal_code",
                "/properties/catalog/properties/context/properties/postal_code");
        paths.put("catalog.context.language",
                "/properties/catalog/properties/context/properties/language");
        paths.put("catalog.context.currency",
                "/properties/catalog/properties/context/properties/currency");
        paths.put("catalog.context.intent",
                "/properties/catalog/properties/context/properties/intent");
        paths.put("catalog.signals", "/properties/catalog/properties/signals");
        paths.put("catalog.signals.dev.ucp.buyer_ip",
                "/properties/catalog/properties/signals/properties/dev.ucp.buyer_ip");
        paths.put("catalog.signals.dev.ucp.user_agent",
                "/properties/catalog/properties/signals/properties/dev.ucp.user_agent");
        paths.put("catalog.filters", "/properties/catalog/properties/filters");
        paths.put("catalog.filters.available",
                "/properties/catalog/properties/filters/properties/available");
        paths.put("catalog.filters.condition",
                "/properties/catalog/properties/filters/properties/condition");
        paths.put("catalog.filters.ships_to",
                "/properties/catalog/properties/filters/properties/ships_to");
        paths.put("catalog.filters.ships_to.country",
                "/properties/catalog/properties/filters/properties/ships_to/properties/country");
        paths.put("catalog.filters.ships_to.region",
                "/properties/catalog/properties/filters/properties/ships_to/properties/region");
        paths.put("catalog.filters.ships_to.postal_code",
                "/properties/catalog/properties/filters/properties/ships_to/properties/postal_code");
        paths.put("catalog.filters.ships_from",
                "/properties/catalog/properties/filters/properties/ships_from");
        paths.put("catalog.filters.ships_from[]",
                "/properties/catalog/properties/filters/properties/ships_from/items");
        paths.put("catalog.filters.ships_from[].country",
                "/properties/catalog/properties/filters/properties/ships_from/items/properties/country");
        paths.put("catalog.filters.price",
                "/properties/catalog/properties/filters/properties/price");
        paths.put("catalog.filters.price.min",
                "/properties/catalog/properties/filters/properties/price/properties/min");
        paths.put("catalog.filters.price.max",
                "/properties/catalog/properties/filters/properties/price/properties/max");
        paths.put("catalog.filters.shops",
                "/properties/catalog/properties/filters/properties/shops");
        paths.put("catalog.filters.shops[]",
                "/properties/catalog/properties/filters/properties/shops/items");
        paths.put("catalog.filters.categories",
                "/properties/catalog/properties/filters/properties/categories");
        paths.put("catalog.filters.categories[]",
                "/properties/catalog/properties/filters/properties/categories/items");
        paths.put("catalog.filters.attributes",
                "/properties/catalog/properties/filters/properties/attributes");
        paths.put("catalog.filters.attributes[]",
                "/properties/catalog/properties/filters/properties/attributes/items");
        paths.put("catalog.filters.attributes[].name",
                "/properties/catalog/properties/filters/properties/attributes/items/properties/name");
        paths.put("catalog.filters.attributes[].values",
                "/properties/catalog/properties/filters/properties/attributes/items/properties/values");
        paths.put("catalog.filters.attributes[].values[]",
                "/properties/catalog/properties/filters/properties/attributes/items/properties/values/items");
        paths.put("catalog.filters.rating",
                "/properties/catalog/properties/filters/properties/rating");
        paths.put("catalog.filters.rating.variant",
                "/properties/catalog/properties/filters/properties/rating/properties/variant");
        paths.put("catalog.filters.rating.variant.min",
                "/properties/catalog/properties/filters/properties/rating/properties/variant/properties/min");
        paths.put("catalog.filters.rating.variant.min_count",
                "/properties/catalog/properties/filters/properties/rating/properties/variant/properties/min_count");
        paths.put("catalog.filters.price_tier",
                "/properties/catalog/properties/filters/properties/price_tier");
        paths.put("catalog.filters.price_tier[]",
                "/properties/catalog/properties/filters/properties/price_tier/items");
        paths.put("catalog.view", "/properties/catalog/properties/view");
        paths.put("catalog.pagination", "/properties/catalog/properties/pagination");
        paths.put("catalog.pagination.cursor",
                "/properties/catalog/properties/pagination/properties/cursor");
        paths.put("catalog.pagination.limit",
                "/properties/catalog/properties/pagination/properties/limit");
        return Map.copyOf(paths);
    }

    private static Map<String, String> requiredSearchSchemaTypes() {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("meta", "object");
        types.put("meta.ucp-agent", "object");
        types.put("meta.ucp-agent.profile", "string");
        types.put("catalog", "object");
        types.put("catalog.query", "string");
        types.put("catalog.like", "array");
        types.put("catalog.context", "object");
        types.put("catalog.context.address_country", "string");
        types.put("catalog.context.address_region", "string");
        types.put("catalog.context.postal_code", "string");
        types.put("catalog.context.language", "string");
        types.put("catalog.context.currency", "string");
        types.put("catalog.context.intent", "string");
        types.put("catalog.signals", "object");
        types.put("catalog.signals.dev.ucp.buyer_ip", "string");
        types.put("catalog.signals.dev.ucp.user_agent", "string");
        types.put("catalog.filters", "object");
        types.put("catalog.filters.available", "boolean");
        types.put("catalog.filters.condition", "array");
        types.put("catalog.filters.ships_to", "object");
        types.put("catalog.filters.ships_to.country", "string");
        types.put("catalog.filters.ships_to.region", "string");
        types.put("catalog.filters.ships_to.postal_code", "string");
        types.put("catalog.filters.ships_from", "array");
        types.put("catalog.filters.ships_from[]", "object");
        types.put("catalog.filters.ships_from[].country", "string");
        types.put("catalog.filters.price", "object");
        types.put("catalog.filters.price.min", "integer");
        types.put("catalog.filters.price.max", "integer");
        types.put("catalog.filters.shops", "array");
        types.put("catalog.filters.shops[]", "string");
        types.put("catalog.filters.categories", "array");
        types.put("catalog.filters.categories[]", "string");
        types.put("catalog.filters.attributes", "array");
        types.put("catalog.filters.attributes[]", "object");
        types.put("catalog.filters.attributes[].name", "string");
        types.put("catalog.filters.attributes[].values", "array");
        types.put("catalog.filters.attributes[].values[]", "string");
        types.put("catalog.filters.rating", "object");
        types.put("catalog.filters.rating.variant", "object");
        types.put("catalog.filters.rating.variant.min", "number");
        types.put("catalog.filters.rating.variant.min_count", "integer");
        types.put("catalog.filters.price_tier", "array");
        types.put("catalog.filters.price_tier[]", "string");
        types.put("catalog.view", "string");
        types.put("catalog.pagination", "object");
        types.put("catalog.pagination.cursor", "string");
        types.put("catalog.pagination.limit", "integer");
        return Map.copyOf(types);
    }

    record ResolvedRoute(URI endpoint, boolean runtimeVerified) {

        private static ResolvedRoute configured(URI endpoint) {
            return new ResolvedRoute(endpoint, false);
        }
    }

    private record VerifiedSnapshot(ResolvedRoute route, Instant verifiedAt) {
    }
}
