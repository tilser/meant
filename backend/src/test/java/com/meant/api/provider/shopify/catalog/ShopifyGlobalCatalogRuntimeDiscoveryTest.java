package com.meant.api.provider.shopify.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.plugin.transport.client.UcpMcpClient;
import com.meant.api.plugin.transport.profile.AgentIdentity;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ShopifyGlobalCatalogRuntimeDiscoveryTest {

    private static final URI CONFIGURED_ENDPOINT =
            URI.create("https://catalog.shopify.test/api/ucp/configured");
    private static final URI DISCOVERED_ENDPOINT =
            URI.create("https://catalog.shopify.test/api/ucp/runtime-v2");
    private static final URI PROFILE =
            URI.create("https://catalog.shopify.test/.well-known/ucp");
    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<ShopifyGlobalCatalogRuntimeDiscovery> discoveries = new ArrayList<>();

    @AfterEach
    void closeDiscoveries() {
        discoveries.forEach(ShopifyGlobalCatalogRuntimeDiscovery::close);
    }

    @Test
    void discoversChangedEndpointValidatesLiveSchemaSendsAgentProfileMetaAndCachesSnapshot() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        Harness harness = harness(clock);
        harness.server().expect(requestTo(PROFILE))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(profile(DISCOVERED_ENDPOINT)));
        harness.server().expect(requestTo(DISCOVERED_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"method\":\"tools/list\"")))
                .andExpect(jsonPath("$.params.arguments.meta['ucp-agent'].profile")
                        .value("http://localhost:8080/.well-known/ucp-agent.json"))
                .andRespond(json(toolsResponse(validSearchInputSchema())));

        assertThat(harness.discovery().endpoint()).isEqualTo(DISCOVERED_ENDPOINT);
        assertThat(harness.discovery().currentSearchParameterContract())
                .contains(
                        "Runtime-verified",
                        "filters.ships_from: one or more ISO countries",
                        "Origin region and postal code are not supported as hard",
                        "buyer IP and user agent"
                );

        // A second route/contract read stays inside the configured TTL and performs no HTTP work.
        assertThat(harness.discovery().endpoint()).isEqualTo(DISCOVERED_ENDPOINT);
        harness.server().verify();
    }

    @Test
    void selectsConfiguredVersionProfileBeforeSelectingMcpEndpoint() throws Exception {
        Harness harness = harness(new MutableClock(NOW));
        URI versionProfile = URI.create("https://catalog.shopify.test/.well-known/ucp/2026-04-08");
        ObjectNode root = profile(DISCOVERED_ENDPOINT);
        ObjectNode rootUcp = (ObjectNode) root.get("ucp");
        rootUcp.put("version", "2026-01-23");
        rootUcp.putObject("supported_versions")
                .put("2026-04-08", versionProfile.toString());

        harness.server().expect(requestTo(PROFILE)).andRespond(json(root));
        harness.server().expect(requestTo(versionProfile)).andRespond(json(profile(DISCOVERED_ENDPOINT)));
        harness.server().expect(requestTo(DISCOVERED_ENDPOINT))
                .andRespond(json(toolsResponse(validSearchInputSchema())));

        assertThat(harness.discovery().endpoint()).isEqualTo(DISCOVERED_ENDPOINT);
        harness.server().verify();
    }

    @Test
    void usesLastKnownGoodAfterTransientRefreshFailure() throws Exception {
        MutableClock clock = new MutableClock(NOW);
        Harness harness = harness(clock);
        harness.server().expect(requestTo(PROFILE)).andRespond(json(profile(DISCOVERED_ENDPOINT)));
        harness.server().expect(requestTo(DISCOVERED_ENDPOINT))
                .andRespond(json(toolsResponse(validSearchInputSchema())));
        harness.server().expect(requestTo(PROFILE))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(harness.discovery().endpoint()).isEqualTo(DISCOVERED_ENDPOINT);
        clock.advance(Duration.ofMinutes(6));

        assertThat(harness.discovery().endpoint()).isEqualTo(DISCOVERED_ENDPOINT);
        assertThat(harness.discovery().currentSearchParameterContract()).contains("Runtime-verified");
        harness.server().verify();
    }

    @Test
    void usesClearlyLabeledConfiguredBaselineOnlyForTransientColdStartFailure() {
        Harness harness = harness(new MutableClock(NOW));
        harness.server().expect(requestTo(PROFILE))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        ShopifyGlobalCatalogRuntimeDiscovery.ResolvedRoute route = harness.discovery().resolveRoute();

        assertThat(route.endpoint()).isEqualTo(CONFIGURED_ENDPOINT);
        assertThat(route.runtimeVerified()).isFalse();
        assertThat(harness.discovery().currentSearchParameterContract())
                .contains("Configured, locally validated cold-start baseline")
                .doesNotContain("Runtime-verified");
        harness.server().verify();
    }

    @Test
    void usesConfiguredRouteWithoutNetworkWhenRuntimeDiscoveryIsDisabled() {
        Harness harness = harness(new MutableClock(NOW), false);

        ShopifyGlobalCatalogRuntimeDiscovery.ResolvedRoute route = harness.discovery().resolveRoute();

        assertThat(route.endpoint()).isEqualTo(CONFIGURED_ENDPOINT);
        assertThat(route.runtimeVerified()).isFalse();
        assertThat(harness.discovery().currentSearchParameterContract())
                .contains("Configured, locally validated cold-start baseline")
                .doesNotContain("Runtime-verified");
        harness.server().verify();
    }

    @Test
    void explicitCapabilityIncompatibilityFailsClosedWithoutConfiguredEndpointFallback() {
        Harness harness = harness(new MutableClock(NOW));
        ObjectNode incompatible = profile(DISCOVERED_ENDPOINT);
        ((ObjectNode) incompatible.at("/ucp/capabilities")).remove("dev.shopify.catalog.global");
        harness.server().expect(requestTo(PROFILE)).andRespond(json(incompatible));

        assertThatThrownBy(() -> harness.discovery().endpoint())
                .isInstanceOf(ShopifyGlobalCatalogDiscoveryException.class)
                .hasMessageContaining("does not advertise dev.shopify.catalog.global")
                .extracting(exception -> ((ShopifyGlobalCatalogDiscoveryException) exception).incompatible())
                .isEqualTo(true);
        harness.server().verify();
    }

    @Test
    void explicitSearchSchemaTypeDriftFailsClosedInsteadOfUsingConfiguredEndpoint() {
        Harness harness = harness(new MutableClock(NOW));
        ObjectNode schema = validSearchInputSchema();
        ((ObjectNode) schema.at("/properties/catalog/properties/query")).put("type", "integer");
        harness.server().expect(requestTo(PROFILE)).andRespond(json(profile(DISCOVERED_ENDPOINT)));
        harness.server().expect(requestTo(DISCOVERED_ENDPOINT))
                .andRespond(json(toolsResponse(schema)));

        assertThatThrownBy(() -> harness.discovery().endpoint())
                .isInstanceOf(ShopifyGlobalCatalogDiscoveryException.class)
                .hasMessageContaining("changed the type of catalog.query");
        harness.server().verify();
    }

    @Test
    void liveCategoryStringItemContractRejectsDocumentationAheadObjectShape() {
        Harness harness = harness(new MutableClock(NOW));
        ObjectNode schema = validSearchInputSchema();
        ObjectNode categoryItems = (ObjectNode) schema.at(
                "/properties/catalog/properties/filters/properties/categories/items");
        categoryItems.put("type", "object");
        categoryItems.putArray("required").add("id");
        scalarProperty(categoryItems.putObject("properties"), "id", "string");
        harness.server().expect(requestTo(PROFILE)).andRespond(json(profile(DISCOVERED_ENDPOINT)));
        harness.server().expect(requestTo(DISCOVERED_ENDPOINT))
                .andRespond(json(toolsResponse(schema)));

        assertThatThrownBy(() -> harness.discovery().endpoint())
                .isInstanceOf(ShopifyGlobalCatalogDiscoveryException.class)
                .hasMessageContaining("changed the type of catalog.filters.categories[]");
        harness.server().verify();
    }

    @Test
    void acceptsLivePaginationSchemaWhereLimitExistsButRemainsOptional() {
        Harness harness = harness(new MutableClock(NOW));
        ObjectNode schema = validSearchInputSchema();
        assertThat(schema.at("/properties/catalog/properties/pagination/properties/limit/type")
                .asString()).isEqualTo("integer");
        assertThat(schema.at("/properties/catalog/properties/pagination/required").isMissingNode())
                .isTrue();
        harness.server().expect(requestTo(PROFILE)).andRespond(json(profile(DISCOVERED_ENDPOINT)));
        harness.server().expect(requestTo(DISCOVERED_ENDPOINT))
                .andRespond(json(toolsResponse(schema)));

        assertThat(harness.discovery().endpoint()).isEqualTo(DISCOVERED_ENDPOINT);
        harness.server().verify();
    }

    @Test
    void catalogCannotMakeEitherSearchModeUnconditionallyRequired() {
        Harness harness = harness(new MutableClock(NOW));
        ObjectNode schema = validSearchInputSchema();
        ((ObjectNode) schema.at("/properties/catalog")).putArray("required").add("query");
        harness.server().expect(requestTo(PROFILE)).andRespond(json(profile(DISCOVERED_ENDPOINT)));
        harness.server().expect(requestTo(DISCOVERED_ENDPOINT))
                .andRespond(json(toolsResponse(schema)));

        assertThatThrownBy(() -> harness.discovery().endpoint())
                .isInstanceOf(ShopifyGlobalCatalogDiscoveryException.class)
                .hasMessageContaining("search_catalog.catalog added an unsupported required field");
        harness.server().verify();
    }

    @Test
    void contextUnknownRequiredFieldDriftFailsClosed() {
        Harness harness = harness(new MutableClock(NOW));
        ObjectNode schema = validSearchInputSchema();
        ((ObjectNode) schema.at("/properties/catalog/properties/context"))
                .putArray("required")
                .add("session_id");
        harness.server().expect(requestTo(PROFILE)).andRespond(json(profile(DISCOVERED_ENDPOINT)));
        harness.server().expect(requestTo(DISCOVERED_ENDPOINT))
                .andRespond(json(toolsResponse(schema)));

        assertThatThrownBy(() -> harness.discovery().endpoint())
                .isInstanceOf(ShopifyGlobalCatalogDiscoveryException.class)
                .hasMessageContaining("search_catalog.catalog.context added an unsupported required field");
        harness.server().verify();
    }

    @Test
    void filtersNonArrayRequiredDeclarationFailsClosed() {
        Harness harness = harness(new MutableClock(NOW));
        ObjectNode schema = validSearchInputSchema();
        ((ObjectNode) schema.at("/properties/catalog/properties/filters"))
                .put("required", "available");
        harness.server().expect(requestTo(PROFILE)).andRespond(json(profile(DISCOVERED_ENDPOINT)));
        harness.server().expect(requestTo(DISCOVERED_ENDPOINT))
                .andRespond(json(toolsResponse(schema)));

        assertThatThrownBy(() -> harness.discovery().endpoint())
                .isInstanceOf(ShopifyGlobalCatalogDiscoveryException.class)
                .hasMessageContaining("search_catalog.catalog.filters has a non-array required-field declaration");
        harness.server().verify();
    }

    @Test
    void explicitSchemaIncompatibilityDoesNotHideBehindExpiredLastKnownGood() {
        MutableClock clock = new MutableClock(NOW);
        Harness harness = harness(clock);
        ObjectNode changedSchema = validSearchInputSchema();
        ((ObjectNode) changedSchema.at("/properties/catalog/properties/query")).put("type", "integer");
        harness.server().expect(requestTo(PROFILE)).andRespond(json(profile(DISCOVERED_ENDPOINT)));
        harness.server().expect(requestTo(DISCOVERED_ENDPOINT))
                .andRespond(json(toolsResponse(validSearchInputSchema())));
        harness.server().expect(requestTo(PROFILE)).andRespond(json(profile(DISCOVERED_ENDPOINT)));
        harness.server().expect(requestTo(DISCOVERED_ENDPOINT))
                .andRespond(json(toolsResponse(changedSchema)));

        assertThat(harness.discovery().endpoint()).isEqualTo(DISCOVERED_ENDPOINT);
        clock.advance(Duration.ofMinutes(6));

        assertThatThrownBy(() -> harness.discovery().endpoint())
                .isInstanceOf(ShopifyGlobalCatalogDiscoveryException.class)
                .hasMessageContaining("changed the type of catalog.query");
        harness.server().verify();
    }

    @Test
    void requiredFieldIncompatibilityDoesNotHideBehindExpiredLastKnownGood() {
        MutableClock clock = new MutableClock(NOW);
        Harness harness = harness(clock);
        ObjectNode changedSchema = validSearchInputSchema();
        ((ObjectNode) changedSchema.at("/properties/catalog/properties/filters"))
                .putArray("required")
                .add("inventory_status");
        harness.server().expect(requestTo(PROFILE)).andRespond(json(profile(DISCOVERED_ENDPOINT)));
        harness.server().expect(requestTo(DISCOVERED_ENDPOINT))
                .andRespond(json(toolsResponse(validSearchInputSchema())));
        harness.server().expect(requestTo(PROFILE)).andRespond(json(profile(DISCOVERED_ENDPOINT)));
        harness.server().expect(requestTo(DISCOVERED_ENDPOINT))
                .andRespond(json(toolsResponse(changedSchema)));

        assertThat(harness.discovery().endpoint()).isEqualTo(DISCOVERED_ENDPOINT);
        clock.advance(Duration.ofMinutes(6));

        assertThatThrownBy(() -> harness.discovery().endpoint())
                .isInstanceOf(ShopifyGlobalCatalogDiscoveryException.class)
                .hasMessageContaining("search_catalog.catalog.filters added an unsupported required field");
        harness.server().verify();
    }

    @Test
    void explicitShipsFromRequiredFieldDriftFailsClosed() {
        Harness harness = harness(new MutableClock(NOW));
        ObjectNode schema = validSearchInputSchema();
        ((tools.jackson.databind.node.ArrayNode) schema.at(
                "/properties/catalog/properties/filters/properties/ships_from/items/required"))
                .add("postal_code");
        harness.server().expect(requestTo(PROFILE)).andRespond(json(profile(DISCOVERED_ENDPOINT)));
        harness.server().expect(requestTo(DISCOVERED_ENDPOINT))
                .andRespond(json(toolsResponse(schema)));

        assertThatThrownBy(() -> harness.discovery().endpoint())
                .isInstanceOf(ShopifyGlobalCatalogDiscoveryException.class)
                .hasMessageContaining("added an unsupported required field");
        harness.server().verify();
    }

    private Harness harness(MutableClock clock) {
        return harness(clock, true);
    }

    private Harness harness(MutableClock clock, boolean runtimeDiscoveryEnabled) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UcpMcpClient mcpClient = new UcpMcpClient(
                new AgentIdentity(
                        URI.create("http://localhost:8080/.well-known/ucp-agent.json"),
                        "2026-04-08",
                        "test-key"
                ),
                objectMapper
        );
        ShopifyGlobalCatalogRuntimeDiscovery discovery = new ShopifyGlobalCatalogRuntimeDiscovery(
                builder.build(),
                mcpClient,
                properties(runtimeDiscoveryEnabled),
                objectMapper,
                clock
        );
        discoveries.add(discovery);
        return new Harness(discovery, server);
    }

    private ShopifyGlobalCatalogProperties properties() {
        return properties(true);
    }

    private ShopifyGlobalCatalogProperties properties(boolean runtimeDiscoveryEnabled) {
        return new ShopifyGlobalCatalogProperties(
                true,
                runtimeDiscoveryEnabled,
                CONFIGURED_ENDPOINT,
                Set.of("catalog.shopify.test"),
                "2026-04-08",
                Duration.ofMinutes(5),
                10,
                50,
                50,
                200,
                16,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                3,
                Duration.ofSeconds(30)
        );
    }

    private ObjectNode profile(URI endpoint) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode ucp = root.putObject("ucp");
        ucp.put("version", "2026-04-08");
        ObjectNode services = ucp.putObject("services");
        services.putArray("dev.ucp.shopping")
                .addObject()
                .put("version", "2026-04-08")
                .put("transport", "mcp")
                .put("endpoint", endpoint.toString());

        ObjectNode capabilities = ucp.putObject("capabilities");
        capabilities.putArray("dev.ucp.shopping.catalog.search")
                .addObject()
                .put("version", "2026-04-08");
        capabilities.putArray("dev.shopify.catalog.global")
                .addObject()
                .put("version", "2026-04-08")
                .putArray("extends")
                .add("dev.ucp.shopping.catalog.search");
        return root;
    }

    private ObjectNode toolsResponse(ObjectNode inputSchema) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", 1);
        ObjectNode tool = response.putObject("result")
                .putArray("tools")
                .addObject();
        tool.put("name", "search_catalog");
        tool.set("inputSchema", inputSchema);
        return response;
    }

    private ObjectNode validSearchInputSchema() {
        ObjectNode root = objectSchema();
        root.putArray("required").add("meta").add("catalog");
        ObjectNode rootProperties = properties(root);

        ObjectNode meta = objectProperty(rootProperties, "meta");
        meta.putArray("required").add("ucp-agent");
        ObjectNode ucpAgent = objectProperty(properties(meta), "ucp-agent");
        ucpAgent.putArray("required").add("profile");
        scalarProperty(properties(ucpAgent), "profile", "string");

        ObjectNode catalog = objectProperty(rootProperties, "catalog");
        ObjectNode catalogProperties = properties(catalog);
        scalarProperty(catalogProperties, "query", "string");

        ObjectNode like = arrayProperty(catalogProperties, "like");
        ObjectNode itemReference = like.putObject("items")
                .putArray("oneOf")
                .addObject();
        itemReference.put("type", "object");
        itemReference.putArray("required").add("id");
        scalarProperty(itemReference.putObject("properties"), "id", "string");

        ObjectNode context = objectProperty(catalogProperties, "context");
        for (String field : List.of(
                "address_country", "address_region", "postal_code", "language", "currency", "intent")) {
            scalarProperty(properties(context), field, "string");
        }

        ObjectNode signals = objectProperty(catalogProperties, "signals");
        scalarProperty(properties(signals), "dev.ucp.buyer_ip", "string");
        scalarProperty(properties(signals), "dev.ucp.user_agent", "string");

        ObjectNode filters = objectProperty(catalogProperties, "filters");
        ObjectNode filterProperties = properties(filters);
        scalarProperty(filterProperties, "available", "boolean");
        ObjectNode condition = arrayProperty(filterProperties, "condition");
        condition.put("description", "Known values: 'new', 'secondhand'.");
        condition.putObject("items").put("type", "string");

        ObjectNode shipsTo = objectProperty(filterProperties, "ships_to");
        shipsTo.putArray("required").add("country");
        scalarProperty(properties(shipsTo), "country", "string");
        scalarProperty(properties(shipsTo), "region", "string");
        scalarProperty(properties(shipsTo), "postal_code", "string");

        ObjectNode shipsFrom = arrayProperty(filterProperties, "ships_from");
        ObjectNode origin = objectSchema();
        origin.putArray("required").add("country");
        scalarProperty(properties(origin), "country", "string");
        shipsFrom.set("items", origin);

        ObjectNode price = objectProperty(filterProperties, "price");
        scalarProperty(properties(price), "min", "integer");
        scalarProperty(properties(price), "max", "integer");

        arrayOfScalarProperty(filterProperties, "shops", "string");
        arrayOfScalarProperty(filterProperties, "categories", "string");

        ObjectNode attributes = arrayProperty(filterProperties, "attributes");
        ObjectNode attribute = objectSchema();
        attribute.putArray("required").add("name").add("values");
        ObjectNode attributeName = scalarProperty(properties(attribute), "name", "string");
        attributeName.put("description", "Supported attributes: Color, Size, Target gender.");
        arrayOfScalarProperty(properties(attribute), "values", "string");
        attributes.set("items", attribute);

        ObjectNode rating = objectProperty(filterProperties, "rating");
        ObjectNode variant = objectProperty(properties(rating), "variant");
        scalarProperty(properties(variant), "min", "number");
        scalarProperty(properties(variant), "min_count", "integer");

        ObjectNode priceTier = arrayOfScalarProperty(filterProperties, "price_tier", "string");
        priceTier.put("description", "Well-known values: low, medium, high.");

        scalarProperty(catalogProperties, "view", "string");
        ObjectNode pagination = objectProperty(catalogProperties, "pagination");
        scalarProperty(properties(pagination), "cursor", "string");
        scalarProperty(properties(pagination), "limit", "integer");
        return root;
    }

    private ObjectNode objectSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    private ObjectNode objectProperty(ObjectNode properties, String name) {
        ObjectNode schema = properties.putObject(name);
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    private ObjectNode arrayProperty(ObjectNode properties, String name) {
        ObjectNode schema = properties.putObject(name);
        schema.put("type", "array");
        return schema;
    }

    private ObjectNode arrayOfScalarProperty(ObjectNode properties, String name, String itemType) {
        ObjectNode schema = arrayProperty(properties, name);
        schema.putObject("items").put("type", itemType);
        return schema;
    }

    private ObjectNode scalarProperty(ObjectNode properties, String name, String type) {
        return properties.putObject(name).put("type", type);
    }

    private ObjectNode properties(ObjectNode schema) {
        return (ObjectNode) schema.get("properties");
    }

    private org.springframework.test.web.client.ResponseCreator json(ObjectNode body) {
        try {
            return withSuccess(objectMapper.writeValueAsString(body), MediaType.APPLICATION_JSON);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Harness(
            ShopifyGlobalCatalogRuntimeDiscovery discovery,
            MockRestServiceServer server
    ) {
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
