package com.meant.api.plugin.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.create.CreateCheckoutCapability;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutArguments;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutRequest;
import com.meant.api.plugin.checkout.get.GetCheckoutCapability;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutArguments;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.checkout.update.UpdateCheckoutCapability;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutArguments;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CheckoutCapabilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createBuildsTypedArgumentsAndParsesTypedResponse() {
        CreateCheckoutCapability capability = new CreateCheckoutCapability(objectMapper);

        CreateCheckoutArguments arguments = capability.buildArguments(
                new CreateCheckoutRequest("gid://shopify/Cart/1"),
                NegotiatedCapabilities.none()
        );
        UcpCheckoutResponse response = capability.parseResponse(new UcpToolResponse(
                checkoutResponseJson(),
                null,
                NegotiatedCapabilities.none()
        ));

        assertThat(arguments.checkout().cartId()).isEqualTo("gid://shopify/Cart/1");
        assertThat(response.resolvedCheckout().id()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(response.resolvedCheckout().cartId()).isEqualTo("gid://shopify/Cart/1");
        assertThat(response.resolvedCheckout().continueUrl()).isEqualTo("https://merchant.example/continue");
        assertThat(response.resolvedCheckout().expiresAt()).isEqualTo(Instant.parse("2026-06-16T12:05:00Z"));
    }

    @Test
    void getBuildsTypedArgumentsAndParsesStructuredContentResponse() {
        GetCheckoutCapability capability = new GetCheckoutCapability(objectMapper);

        GetCheckoutArguments arguments = capability.buildArguments(
                new GetCheckoutRequest("gid://shopify/Checkout/1"),
                NegotiatedCapabilities.none()
        );
        UcpCheckoutResponse response = capability.parseResponse(new UcpToolResponse(null,
                Map.of(
                        "checkout", Map.of(
                                "id", "gid://shopify/Checkout/1",
                                "continue_url", "https://merchant.example/continue"
                        ),
                        "errors", List.of()
                ),
                NegotiatedCapabilities.none()));

        assertThat(arguments.checkoutId()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(response.resolvedCheckout().id()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(response.resolvedCheckout().continueUrl()).isEqualTo("https://merchant.example/continue");
    }

    @Test
    void updateBuildsBuyerEmailAndShippingArgumentsAndParsesTypedResponse() {
        UpdateCheckoutCapability capability = new UpdateCheckoutCapability(objectMapper);

        UpdateCheckoutArguments arguments = capability.buildArguments(
                new UpdateCheckoutRequest(
                        "gid://shopify/Checkout/1",
                        Map.of("id", "buyer-1"),
                        "ada@example.com",
                        Map.of(
                                "address1", "1 Main St",
                                "city", "New York",
                                "country_code", "US"
                        )
                ),
                NegotiatedCapabilities.none()
        );
        UcpCheckoutResponse response = capability.parseResponse(new UcpToolResponse(
                checkoutResponseJson(),
                null,
                NegotiatedCapabilities.none()
        ));

        assertThat(arguments.checkoutId()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(arguments.buyer()).containsEntry("id", "buyer-1");
        assertThat(arguments.email()).isEqualTo("ada@example.com");
        assertThat(arguments.fulfillment().shippingAddress()).containsEntry("city", "New York");
        assertThat(response.resolvedCheckout().status()).isEqualTo("open");
    }

    private String checkoutResponseJson() {
        return """
                {
                  "instructions": "Open checkout in browser",
                  "checkout": {
                    "id": "gid://shopify/Checkout/1",
                    "cart_id": "gid://shopify/Cart/1",
                    "status": "open",
                    "checkout_url": "https://merchant.example/checkout",
                    "continue_url": "https://merchant.example/continue",
                    "created_at": "2026-06-16T11:05:00Z",
                    "updated_at": "2026-06-16T11:05:01Z",
                    "expires_at": "2026-06-16T12:05:00Z",
                    "buyer": {
                      "email": "ada@example.com"
                    },
                    "fulfillment": {
                      "shipping_address": {
                        "city": "New York"
                      }
                    },
                    "messages": []
                  },
                  "errors": []
                }
                """;
    }
}
