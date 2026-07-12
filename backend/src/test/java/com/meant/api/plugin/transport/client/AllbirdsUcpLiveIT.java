package com.meant.api.plugin.transport.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.transport.profile.AgentIdentity;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in smoke test against a real merchant. It creates a temporary cart and checkout session, but
 * never submits buyer data, a payment instrument, or a completion request.
 */
class AllbirdsUcpLiveIT {

    private static final URI PROFILE = URI.create("https://www.allbirds.com/.well-known/ucp");
    private static final String ENABLE_ENV = "MEANT_LIVE_ALLBIRDS_UCP_TEST";
    private static final String AGENT_PROFILE =
            "https://www.machinecommerce.dev/ucp/agent-profile/ucp-agent.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.builder().build();
    private final UcpMcpClient mcpClient = new UcpMcpClient(
            new AgentIdentity(URI.create(AGENT_PROFILE), "2026-04-08", "live-smoke-test"),
            objectMapper
    );

    @BeforeAll
    static void requireExplicitOptIn() {
        Assumptions.assumeTrue(
                Boolean.parseBoolean(System.getenv(ENABLE_ENV)),
                () -> "Set " + ENABLE_ENV + "=true to run the live Allbirds UCP smoke test"
        );
    }

    @Test
    @Timeout(120)
    void discoversSearchesCreatesCartAndCreatesRecoverableCheckout() {
        Map<String, Object> profile = restClient.get().uri(PROFILE).retrieve().body(Map.class);
        URI endpoint = shoppingMcpEndpoint(profile);
        assertThat(capabilities(profile)).contains(
                "dev.ucp.shopping.catalog.search",
                "dev.ucp.shopping.cart",
                "dev.ucp.shopping.checkout"
        );
        assertThat(mcpClient.listTools(restClient, endpoint))
                .contains("search_catalog", "create_cart", "create_checkout", "get_checkout");

        Map<String, Object> search = payload(mcpClient.callTool(
                restClient,
                endpoint,
                "search_catalog",
                Map.of("catalog", Map.of(
                        "query", "men's shoes",
                        "context", Map.of(
                                "address_country", "US",
                                "language", "en-US",
                                "currency", "USD"
                        ),
                        "filters", Map.of("available", true),
                        "pagination", Map.of("limit", 3)
                ))
        ));
        String variantId = firstAvailableVariantId(search);

        Map<String, Object> cart = payload(mcpClient.callTool(
                restClient,
                endpoint,
                "create_cart",
                Map.of("cart", Map.of(
                        "line_items", List.of(Map.of(
                                "quantity", 1,
                                "item", Map.of("id", variantId)
                        )),
                        "context", Map.of(
                                "address_country", "US",
                                "language", "en-US",
                                "currency", "USD",
                                "intent", "test"
                        )
                ))
        ));
        String cartId = requiredString(cart, "id");
        assertThat(firstLineVariantId(cart)).isEqualTo(variantId);

        Map<String, Object> persistedCart = payload(mcpClient.callTool(
                restClient,
                endpoint,
                "get_cart",
                Map.of("id", cartId)
        ));
        assertThat(requiredString(persistedCart, "id")).isEqualTo(cartId);
        assertThat(firstLineVariantId(persistedCart)).isEqualTo(variantId);

        Map<String, Object> checkout = payload(mcpClient.callToolAllowingJsonToolErrors(
                restClient,
                endpoint,
                "create_checkout",
                Map.of("checkout", Map.of("cart_id", cartId, "line_items", List.of())),
                Map.of()
        ));
        String checkoutId = requiredString(checkout, "id");
        assertThat(firstLineVariantId(checkout)).isEqualTo(variantId);
        assertThat(requiredString(checkout, "status"))
                .isIn("incomplete", "requires_escalation", "ready_for_complete");
        assertThat(continueUrl(checkout)).isNotBlank();

        Map<String, Object> persistedCheckout = payload(mcpClient.callToolAllowingJsonToolErrors(
                restClient,
                endpoint,
                "get_checkout",
                Map.of("id", checkoutId),
                Map.of()
        ));
        assertThat(requiredString(persistedCheckout, "id")).isEqualTo(checkoutId);
        assertThat(firstLineVariantId(persistedCheckout)).isEqualTo(variantId);
        assertThat(continueUrl(persistedCheckout)).isNotBlank();
    }

    private URI shoppingMcpEndpoint(Map<String, Object> profile) {
        Map<String, Object> ucp = map(profile.get("ucp"));
        List<Object> services = list(map(ucp.get("services")).get("dev.ucp.shopping"));
        return services.stream()
                .map(this::map)
                .filter(service -> "mcp".equals(service.get("transport")))
                .map(service -> requiredString(service, "endpoint"))
                .map(URI::create)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Allbirds profile has no MCP shopping endpoint"));
    }

    private List<String> capabilities(Map<String, Object> profile) {
        return map(map(profile.get("ucp")).get("capabilities")).keySet().stream().sorted().toList();
    }

    private String firstAvailableVariantId(Map<String, Object> search) {
        return list(search.get("products")).stream()
                .map(this::map)
                .flatMap(product -> list(product.get("variants")).stream())
                .map(this::map)
                .filter(variant -> Boolean.TRUE.equals(map(variant.get("availability")).get("available")))
                .map(variant -> requiredString(variant, "id"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Allbirds search returned no available variant"));
    }

    private String firstLineVariantId(Map<String, Object> payload) {
        Map<String, Object> line = map(list(payload.get("line_items")).getFirst());
        return requiredString(map(line.get("item")), "id");
    }

    private String continueUrl(Map<String, Object> checkout) {
        Object value = checkout.get("continue_url");
        if (value == null) {
            value = checkout.get("url");
        }
        return value == null ? "" : value.toString();
    }

    private Map<String, Object> payload(UcpToolResponse response) {
        if (response.structuredContent() instanceof Map<?, ?> values) {
            return map(values);
        }
        try {
            return objectMapper.readValue(response.textContent(), new TypeReference<>() { });
        } catch (RuntimeException exception) {
            throw new AssertionError("Live merchant response was not a JSON object", exception);
        }
    }

    private String requiredString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        assertThat(value).as(key).isInstanceOf(String.class);
        assertThat(value.toString()).as(key).isNotBlank();
        return value.toString();
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        return source.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        Map.Entry::getValue,
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                ));
    }

    private List<Object> list(Object value) {
        return value instanceof List<?> values ? List.copyOf(values) : List.of();
    }
}
