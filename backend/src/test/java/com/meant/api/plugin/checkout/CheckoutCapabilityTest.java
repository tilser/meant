package com.meant.api.plugin.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.CheckoutCapabilityMetadata;
import com.meant.api.plugin.checkout.create.CreateCheckoutCapability;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutArguments;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutRequest;
import com.meant.api.plugin.checkout.extension.ap2mandate.Ap2MandateExtensionCapability;
import com.meant.api.plugin.checkout.extension.buyerconsent.BuyerConsentExtensionCapability;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentState;
import com.meant.api.plugin.checkout.extension.discount.DiscountExtensionCapability;
import com.meant.api.plugin.checkout.extension.fulfillment.FulfillmentExtensionCapability;
import com.meant.api.plugin.checkout.get.GetCheckoutCapability;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutArguments;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.checkout.update.UpdateCheckoutCapability;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutArguments;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
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
                new CreateCheckoutRequest(
                        "gid://shopify/Cart/1",
                        List.of(),
                        Map.of("email", "ada@example.com"),
                        new BuyerConsentState(true, null, false, null),
                        List.of("SAVE10"),
                        Map.of("methods", List.of(Map.of("id", "method_1", "type", "shipping")))
                ),
                NegotiatedCapabilities.none()
        );
        UcpCheckoutResponse response = capability.parseResponse(new UcpToolResponse(
                checkoutResponseJson(),
                null,
                NegotiatedCapabilities.none()
        ));

        assertThat(arguments.checkout().cartId()).isEqualTo("gid://shopify/Cart/1");
        assertThat(arguments.checkout().buyer()).containsKey("consent");
        assertThat(arguments.checkout().discounts().codes()).containsExactly("SAVE10");
        assertThat(arguments.checkout().fulfillment().methods().getFirst())
                .satisfies(method -> assertThat(method).containsEntry("id", "method_1"));
        assertThat(response.resolvedCheckout().id()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(response.resolvedCheckout().cartId()).isEqualTo("gid://shopify/Cart/1");
        assertThat(response.resolvedCheckout().continueUrl()).isEqualTo("https://merchant.example/continue");
        assertThat(response.resolvedCheckout().expiresAt()).isEqualTo(Instant.parse("2026-06-16T12:05:00Z"));
        assertThat(response.resolvedCheckout().currency()).isEqualTo("USD");
        assertThat(response.resolvedCheckout().discounts().applied()).isEmpty();
        assertThat(response.resolvedCheckout().ap2().merchantAuthorization()).isEqualTo("merchant-signature");
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
                        ),
                        List.of("SAVE10"),
                        Map.of(
                                "methods", List.of(Map.of("id", "method_1", "type", "shipping")),
                                "available_methods", List.of(Map.of("type", "shipping", "line_item_ids", List.of("li_1")))
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
        assertThat(arguments.fulfillment().methods().getFirst())
                .satisfies(method -> assertThat(method).containsEntry("id", "method_1"));
        assertThat(arguments.fulfillment().availableMethods().getFirst())
                .satisfies(method -> assertThat(method).containsEntry("type", "shipping"));
        assertThat(arguments.discounts().codes()).containsExactly("SAVE10");
        assertThat(response.resolvedCheckout().status()).isEqualTo("open");
    }

    @Test
    void rootCheckoutResponseParsesExtensionFields() {
        CreateCheckoutCapability capability = new CreateCheckoutCapability(objectMapper);

        UcpCheckoutResponse response = capability.parseResponse(new UcpToolResponse(
                """
                        {
                          "id": "checkout_456",
                          "status": "ready_for_complete",
                          "currency": "USD",
                          "buyer": {
                            "email": "jane.doe@example.com",
                            "consent": {
                              "analytics": true,
                              "marketing": false
                            }
                          },
                          "discounts": {"codes": ["SAVE10"], "applied": []},
                          "fulfillment": {"methods": []},
                          "ap2": {"merchant_authorization": "merchant-signature"},
                          "line_items": [],
                          "totals": []
                        }
                        """,
                null,
                NegotiatedCapabilities.none()
        ));

        assertThat(response.resolvedCheckout().id()).isEqualTo("checkout_456");
        assertThat(response.resolvedCheckout().buyer().email()).isEqualTo("jane.doe@example.com");
        assertThat(response.resolvedCheckout().discounts().codes()).containsExactly("SAVE10");
        assertThat(response.resolvedCheckout().fulfillment().methods()).isEmpty();
        assertThat(response.resolvedCheckout().ap2().merchantAuthorization()).isEqualTo("merchant-signature");
    }

    @Test
    void checkoutExtensionsAdvertiseDocumentedParentsWithoutStandaloneTools() {
        CapabilityAdvertisement ap2 = new Ap2MandateExtensionCapability().advertisements().getFirst();
        CapabilityAdvertisement buyerConsent = new BuyerConsentExtensionCapability().advertisements().getFirst();
        CapabilityAdvertisement discount = new DiscountExtensionCapability().advertisements().getFirst();
        CapabilityAdvertisement fulfillment = new FulfillmentExtensionCapability().advertisements().getFirst();

        assertThat(ap2.tools()).isEmpty();
        assertThat(ap2.extendsCapabilities()).containsExactly(CheckoutCapabilityMetadata.CHECKOUT);
        assertThat(ap2.config()).containsKey("vp_formats_supported");
        assertThat(buyerConsent.extendsCapabilities()).containsExactly(CheckoutCapabilityMetadata.CHECKOUT);
        assertThat(discount.extendsCapabilities())
                .containsExactly(CheckoutCapabilityMetadata.CHECKOUT, CheckoutCapabilityMetadata.CART);
        assertThat(fulfillment.extendsCapabilities()).containsExactly(CheckoutCapabilityMetadata.CHECKOUT);
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
                    "currency": "USD",
                    "buyer": {
                      "email": "ada@example.com",
                      "consent": {
                        "analytics": true,
                        "marketing": false
                      }
                    },
                    "line_items": [],
                    "totals": [],
                    "discounts": {
                      "codes": ["SAVE10"],
                      "applied": []
                    },
                    "fulfillment": {
                      "shipping_address": {
                        "city": "New York"
                      }
                    },
                    "ap2": {
                      "merchant_authorization": "merchant-signature"
                    },
                    "messages": []
                  },
                  "errors": []
                }
                """;
    }
}
