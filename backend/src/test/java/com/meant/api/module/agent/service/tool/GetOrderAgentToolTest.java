package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.AgentBuyerPayloadSanitizer;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadReferenceService;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.order.constant.OrderState;
import com.meant.api.module.order.service.OrderService;
import com.meant.api.module.order.service.dto.OrderLineResult;
import com.meant.api.module.order.service.dto.OrderResult;
import com.meant.api.module.order.service.query.GetOrderQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GetOrderAgentToolTest {

    @Test
    void exposesTheOfficialMerchantAsMerchantOriginInResultAndArtifact() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000111");
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000222");
        ObjectMapper objectMapper = new ObjectMapper();
        AgentJsonSupport json = new AgentJsonSupport(objectMapper, properties());
        OrderResult order = new OrderResult(
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000000333"),
                "merchant.example",
                "Merchant",
                "remote-order-1",
                "#1001",
                "1001",
                OrderState.PROCESSING,
                "Confirmed",
                "The merchant is preparing this order.",
                "2026-07-23",
                "92.00",
                "92.00",
                "USD",
                1,
                "https://merchant.example/orders/1001",
                List.of(new OrderLineResult(
                        UUID.fromString("00000000-0000-0000-0000-000000000444"),
                        "product-key",
                        "product-1",
                        "Running shoe",
                        "Merchant",
                        "variant-1",
                        "Size 42",
                        "SKU-1",
                        "https://merchant.example/images/shoe.jpg",
                        "https://merchant.example/products/running-shoe",
                        1,
                        "92.00",
                        "92.00",
                        "USD"
                )),
                Instant.parse("2026-07-23T18:00:00Z"),
                Instant.parse("2026-07-23T18:01:00Z")
        );
        StubReferenceService referenceService = new StubReferenceService();
        StubOrderService orderService = new StubOrderService(order);
        GetOrderAgentTool tool = new GetOrderAgentTool(json, referenceService, orderService);

        var result = tool.execute(
                new AgentToolExecutionContext(
                        userId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "show order"
                ),
                "{\"orderId\":\"" + orderId + "\",\"refresh\":false}"
        );

        String buyerResultJson = AgentBuyerPayloadSanitizer.sanitize(result.resultJson());
        assertThat(objectMapper.readTree(buyerResultJson).get("merchantOrigin").asText())
                .isEqualTo("merchant.example");
        assertThat(objectMapper.readTree(buyerResultJson).at("/lines/0/productTitle").asText())
                .isEqualTo("Running shoe");
        assertThat(buyerResultJson).doesNotContain("\"merchantDomain\"");
        assertThat(referenceService.requiredOrderId).isEqualTo(orderId);
        assertThat(orderService.query.userId()).isEqualTo(userId);
        assertThat(orderService.query.refresh()).isFalse();
        assertThat(result.artifacts()).singleElement().satisfies(artifact -> {
            String buyerArtifactJson = AgentBuyerPayloadSanitizer.sanitize(artifact.payloadJson());
            assertThat(objectMapper.readTree(buyerArtifactJson).get("merchantOrigin").asText())
                    .isEqualTo("merchant.example");
            assertThat(buyerArtifactJson).doesNotContain("\"merchantDomain\"");
        });
    }

    private AgentProperties properties() {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, 64000, 2, Duration.ofSeconds(30), Duration.ofSeconds(10),
                Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }

    private static final class StubReferenceService extends AgentProductReadReferenceService {

        private UUID requiredOrderId;

        private StubReferenceService() {
            super(null, null);
        }

        @Override
        public AgentArtifactReference requireOrder(
                AgentToolExecutionContext context,
                UUID orderId
        ) {
            requiredOrderId = orderId;
            return null;
        }
    }

    private static final class StubOrderService extends OrderService {

        private final OrderResult result;
        private GetOrderQuery query;

        private StubOrderService(OrderResult result) {
            super(null, null, null, null, null);
            this.result = result;
        }

        @Override
        public OrderResult get(GetOrderQuery query) {
            this.query = query;
            return result;
        }
    }
}
