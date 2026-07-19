package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentCartResult;
import com.meant.api.module.cart.constant.CartAppliedCodeType;
import com.meant.api.module.cart.service.dto.CartAppliedCodeResult;
import com.meant.api.module.cart.service.dto.CartDeliveryGroupResult;
import com.meant.api.module.cart.service.dto.CartDeliveryMoneyResult;
import com.meant.api.module.cart.service.dto.CartDeliveryOptionResult;
import com.meant.api.module.cart.service.dto.CartLineResult;
import com.meant.api.module.cart.service.dto.CartResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AgentCartArtifactTest {

    private static final UUID CART_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID CART_LINE_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void cartArtifactsRetainAppliedCodesAndDeliveryGroups() throws Exception {
        AgentCartResult result = AgentCartResult.success(List.of(cartResult()));
        AgentCartToolSupport support = new AgentCartToolSupport(
                null,
                null,
                null,
                null,
                objectMapper,
                null,
                new AgentJsonSupport(objectMapper, properties())
        );

        List<AgentArtifact> artifacts = support.artifacts(result);

        assertThat(artifacts).extracting(AgentArtifact::type)
                .containsExactly(AgentArtifactType.CART, AgentArtifactType.CART_LINE);
        JsonNode cart = objectMapper.readTree(artifacts.getFirst().payloadJson());
        assertThat(artifacts.getFirst().payloadJson()).hasSizeGreaterThan(256);
        assertThat(cart.has("truncated")).isFalse();
        assertThat(cart.at("/appliedCodes/0/type").asText()).isEqualTo("DISCOUNT");
        assertThat(cart.at("/appliedCodes/0/code").asText()).isEqualTo("SAVE5");
        assertThat(cart.at("/appliedCodes/0/amount").asText()).isEqualTo("5.00");
        assertThat(cart.at("/deliveryGroups/0/id").asText()).isEqualTo("delivery-group-1");
        assertThat(cart.at("/deliveryGroups/0/deliveryOptions/0/handle").asText())
                .isEqualTo("standard");
        assertThat(cart.at("/deliveryGroups/0/deliveryOptions/0/cost/amount").asText())
                .isEqualTo("4.99");
        assertThat(cart.at("/deliveryGroups/0/selectedDeliveryOption/handle").asText())
                .isEqualTo("standard");
        assertThat(cart.at("/lines/0/offerKey").asText()).isEqualTo("offer-1");

        JsonNode line = objectMapper.readTree(artifacts.getLast().payloadJson());
        assertThat(line.get("cartLineId").asText()).isEqualTo(CART_LINE_ID.toString());
        assertThat(line.get("offerKey").asText()).isEqualTo("offer-1");
    }

    @Test
    void cartArtifactsNeverPresentACompleteLookingPartialLineSet() throws Exception {
        List<CartLineResult> lines = IntStream.range(0, 51).mapToObj(this::cartLine).toList();
        AgentCartResult result = AgentCartResult.success(List.of(cartResult(lines)));
        AgentCartToolSupport support = new AgentCartToolSupport(
                null,
                null,
                null,
                null,
                objectMapper,
                null,
                new AgentJsonSupport(objectMapper, properties())
        );

        List<AgentArtifact> artifacts = support.artifacts(result);

        assertThat(artifacts).hasSize(52);
        JsonNode cart = objectMapper.readTree(artifacts.getFirst().payloadJson());
        assertThat(cart.get("lines")).hasSize(51);
        assertThat(objectMapper.readTree(artifacts.getLast().payloadJson()).get("offerKey").asText())
                .isEqualTo("offer-51");
    }

    private CartResult cartResult() {
        return cartResult(List.of(cartLine(0)));
    }

    private CartResult cartResult(List<CartLineResult> lines) {
        Instant now = Instant.parse("2026-07-19T10:00:00Z");
        CartAppliedCodeResult code = new CartAppliedCodeResult(
                CartAppliedCodeType.DISCOUNT,
                "SAVE5",
                "Save $5",
                true,
                "5.00",
                "USD"
        );
        CartDeliveryOptionResult standard = new CartDeliveryOptionResult(
                "standard",
                "Standard delivery",
                "Arrives in three to five days",
                "STANDARD",
                new CartDeliveryMoneyResult("4.99", "USD"),
                "SHIPPING",
                "3-5 business days",
                "P3D",
                now.plus(Duration.ofDays(3)),
                true
        );
        CartDeliveryGroupResult deliveryGroup = new CartDeliveryGroupResult(
                "delivery-group-1",
                "shipping",
                List.of(standard),
                standard
        );
        return new CartResult(
                CART_ID,
                null,
                "running.example",
                "SHOPIFY",
                null,
                "merchant-1",
                "SHOPIFY:merchant-1",
                "https://running.example/ucp",
                "remote-cart-1",
                "https://running.example/checkout",
                "https://running.example",
                null,
                lines.size(),
                "99.99",
                "95.00",
                "USD",
                true,
                now,
                now,
                now.plus(Duration.ofHours(1)),
                now,
                now,
                now,
                List.of(code),
                lines,
                List.of(deliveryGroup),
                List.of()
        );
    }

    private CartLineResult cartLine(int index) {
        Instant now = Instant.parse("2026-07-19T10:00:00Z");
        int number = index + 1;
        return new CartLineResult(
                new UUID(0, 0x302L + index),
                "remote-line-" + number,
                "product-" + number,
                "Running shoe " + number,
                "variant-" + number,
                "Size 42",
                1,
                "100.00",
                "95.00",
                "USD",
                "offer-" + number,
                "SHOPIFY",
                null,
                "merchant-1",
                now,
                now
        );
    }

    private AgentProperties properties() {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, 256, 2, Duration.ofSeconds(30), Duration.ofSeconds(10),
                Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }
}
