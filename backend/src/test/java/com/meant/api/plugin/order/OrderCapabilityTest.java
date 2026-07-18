package com.meant.api.plugin.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.order.common.dto.UcpOrderResponse;
import com.meant.api.plugin.order.get.GetOrderCapability;
import com.meant.api.plugin.order.get.dto.GetOrderArguments;
import com.meant.api.plugin.order.get.dto.GetOrderRequest;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OrderCapabilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getBuildsTypedArgumentsAndParsesTypedResponse() {
        GetOrderCapability capability = new GetOrderCapability(objectMapper);

        GetOrderArguments arguments = capability.buildArguments(
                new GetOrderRequest("gid://shopify/Order/1"),
                NegotiatedCapabilities.none()
        );
        UcpOrderResponse response = capability.parseResponse(new UcpToolResponse(
                orderResponseJson(),
                null,
                NegotiatedCapabilities.none()
        ));

        assertThat(arguments.orderId()).isEqualTo("gid://shopify/Order/1");
        assertThat(response.order().id()).isEqualTo("gid://shopify/Order/1");
        assertThat(response.order().name()).isEqualTo("#1001");
        assertThat(response.order().processedAt()).isEqualTo(Instant.parse("2026-06-17T10:05:00Z"));
        assertThat(response.order().lineItems()).extracting("variantId")
                .containsExactly("gid://shopify/ProductVariant/1");
        assertThat(response.order().lineItems().getFirst().price().amount()).isEqualTo(1495L);
        assertThat(response.order().lineItems().getFirst().totalPrice().amount()).isEqualTo(2990L);
        assertThat(response.order().cost().totalAmount().amount()).isEqualTo(2990L);
        assertThat(response.messages()).extracting("code").containsExactly("order_loaded");
    }

    @Test
    void getParsesCanonicalMinorUnitsAndLegacyDecimalMoneyAndRoundTripsCanonicalJson() throws Exception {
        GetOrderCapability capability = new GetOrderCapability(objectMapper);

        UcpOrderResponse response = capability.parseResponse(new UcpToolResponse(
                """
                        {
                          "order": {
                            "id": "gid://shopify/Order/3",
                            "total_price": {"amount": 2990, "currency_code": "USD"},
                            "subtotal_price": "29.90",
                            "currency": "USD",
                            "line_items": [
                              {
                                "id": "line-1",
                                "quantity": 2,
                                "price": {"amount": 1495, "currency": "USD"},
                                "total_price": "29.90",
                                "currency": "USD"
                              }
                            ]
                          }
                        }
                        """,
                null,
                NegotiatedCapabilities.none()
        ));

        assertThat(response.order().totalPrice()).isEqualTo(new UcpOrderResponse.Money(2990L, "USD"));
        assertThat(response.order().subtotalPrice()).isEqualTo(new UcpOrderResponse.Money(2990L, null));
        assertThat(response.order().lineItems().getFirst().price())
                .isEqualTo(new UcpOrderResponse.Money(1495L, "USD"));
        assertThat(response.order().lineItems().getFirst().totalPrice())
                .isEqualTo(new UcpOrderResponse.Money(2990L, null));

        String canonicalJson = objectMapper.writeValueAsString(response);
        assertThat(canonicalJson).contains("\"amount\":2990");
        UcpOrderResponse roundTripped = objectMapper.readValue(canonicalJson, UcpOrderResponse.class);
        assertThat(roundTripped.order().totalPrice()).isEqualTo(response.order().totalPrice());
        assertThat(roundTripped.order().lineItems().getFirst().price())
                .isEqualTo(response.order().lineItems().getFirst().price());
    }

    @Test
    void getParsesStructuredContentResponse() {
        GetOrderCapability capability = new GetOrderCapability(objectMapper);

        UcpOrderResponse response = capability.parseResponse(new UcpToolResponse(null,
                objectMapper.valueToTree(Map.of(
                        "order", Map.of(
                                "id", "gid://shopify/Order/2",
                                "name", "#1002",
                                "line_items", List.of()
                        ),
                        "errors", List.of()
                )),
                NegotiatedCapabilities.none()));

        assertThat(response.order().id()).isEqualTo("gid://shopify/Order/2");
        assertThat(response.order().name()).isEqualTo("#1002");
    }

    private String orderResponseJson() {
        return """
                {
                  "instructions": "Show order state",
                  "messages": [
                    {
                      "code": "order_loaded",
                      "severity": "info",
                      "message": "Order loaded"
                    }
                  ],
                  "order": {
                    "id": "gid://shopify/Order/1",
                    "name": "#1001",
                    "order_number": "1001",
                    "financial_status": "paid",
                    "fulfillment_status": "in_transit",
                    "email": "ada@example.com",
                    "created_at": "2026-06-17T10:00:00Z",
                    "processed_at": "2026-06-17T10:05:00Z",
                    "updated_at": "2026-06-17T10:06:00Z",
                    "line_items": [
                      {
                        "id": "gid://shopify/LineItem/1",
                        "title": "Candle",
                        "quantity": 2,
                        "product_id": "gid://shopify/Product/1",
                        "variant_id": "gid://shopify/ProductVariant/1",
                        "variant_title": "3x6",
                        "price": "14.95",
                        "total_price": "29.90",
                        "currency": "USD"
                      }
                    ],
                    "cost": {
                      "total_amount": {"amount": "29.90", "currency": "USD"},
                      "subtotal_amount": {"amount": "29.90", "currency": "USD"}
                    }
                  },
                  "errors": []
                }
                """;
    }
}
