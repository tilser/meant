package com.meant.api.plugin.checkout.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.support.UcpMoney;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CheckoutMoneyDeserializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void treatsIntegerObjectAmountFieldsAsMajorUnits() throws Exception {
        UcpCheckoutResponse response = objectMapper.readValue(
                """
                {
                  "checkout_id": "co_123",
                  "currency": "USD",
                  "total_amount": {"amount": 29, "currency": "USD"},
                  "tax_amount": {"price": "4", "currency": "USD"},
                  "checkout": {
                    "currency": "USD",
                    "line_items": [
                      {"id": "line_1", "quantity": 1, "total_amount": {"value": 29, "currency": "USD"}}
                    ],
                    "totals": [
                      {"type": "minimum", "value": {"min": "12", "currency": "USD"}}
                    ]
                  }
                }
                """,
                UcpCheckoutResponse.class
        );

        assertThat(response.totalAmount().toUcpMoney(null)).isEqualTo(new UcpMoney(2900L, "USD"));
        assertThat(response.taxAmount().toUcpMoney(null)).isEqualTo(new UcpMoney(400L, "USD"));
        assertThat(response.checkout().lineItems().getFirst().totalAmount().toUcpMoney("USD"))
                .isEqualTo(new UcpMoney(2900L, "USD"));
        assertThat(response.checkout().totals().getFirst().resolvedMoney("USD"))
                .isEqualTo(new UcpMoney(1200L, "USD"));
    }

    @Test
    void preservesExplicitMinorObjectFieldsAndScalarMoneyValues() throws Exception {
        UcpCheckoutResponse response = objectMapper.readValue(
                """
                {
                  "checkout_id": "co_123",
                  "currency": "USD",
                  "total_amount": {"amount_minor": 29, "currency": "USD"},
                  "tax_amount": 400
                }
                """,
                UcpCheckoutResponse.class
        );

        assertThat(response.totalAmount().toUcpMoney(null)).isEqualTo(new UcpMoney(29L, "USD"));
        assertThat(response.taxAmount().toUcpMoney("USD")).isEqualTo(new UcpMoney(400L, "USD"));
    }
}
