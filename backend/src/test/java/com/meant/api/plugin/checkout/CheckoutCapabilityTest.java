package com.meant.api.plugin.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.checkout.common.dto.CheckoutBuyer;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.CheckoutCapabilityMetadata;
import com.meant.api.plugin.checkout.common.dto.CheckoutContext;
import com.meant.api.plugin.checkout.create.CreateCheckoutCapability;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutArguments;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutRequest;
import com.meant.api.plugin.checkout.extension.ap2mandate.Ap2MandateExtensionCapability;
import com.meant.api.plugin.checkout.extension.buyerconsent.BuyerConsentExtensionCapability;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentState;
import com.meant.api.plugin.checkout.extension.discount.DiscountExtensionCapability;
import com.meant.api.plugin.checkout.extension.fulfillment.FulfillmentExtensionCapability;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.FulfillmentMethod;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.RetailLocation;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.ShippingDestination;
import com.meant.api.plugin.checkout.get.GetCheckoutCapability;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutArguments;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.checkout.update.UpdateCheckoutCapability;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutArguments;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.support.UcpAttribution;
import com.meant.api.plugin.transport.profile.AgentAttributionProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CheckoutCapabilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentAttributionProperties attributionProperties = new AgentAttributionProperties(
            "app.usemeant.com", "meant", "agentic_commerce");
    private final UcpAttribution attribution = attributionProperties.attribution();

    @Test
    void createBuildsTypedArgumentsAndParsesTypedResponse() throws Exception {
        CreateCheckoutCapability capability = new CreateCheckoutCapability(objectMapper, attributionProperties);

        CreateCheckoutArguments arguments = capability.buildArguments(
                new CreateCheckoutRequest(
                        "gid://shopify/Cart/1",
                        List.of(new CreateCheckoutRequest.LineItem(
                                "gid://shopify/CartLine/1",
                                "gid://shopify/ProductVariant/1",
                                2
                        )),
                        new CheckoutBuyer(null, null, "ada@example.com", null),
                        new BuyerConsentState(true, null, false, null),
                        "USD",
                        List.of("SAVE10"),
                        new CheckoutFulfillment(List.of(new FulfillmentMethod(
                                "method_1", "shipping", List.of(), List.of(), null, List.of())))
                ),
                NegotiatedCapabilities.none()
        );
        UcpCheckoutResponse response = capability.parseResponse(new UcpToolResponse(
                checkoutResponseJson(),
                null,
                NegotiatedCapabilities.none()
        ));
        String serializedArguments = objectMapper.writeValueAsString(arguments);

        assertThat(arguments.checkout().lineItems()).singleElement().satisfies(lineItem -> {
            assertThat(lineItem.item().id()).isEqualTo("gid://shopify/ProductVariant/1");
            assertThat(lineItem.quantity()).isEqualTo(2);
        });
        assertThat(serializedArguments).doesNotContain("gid://shopify/CartLine/1");
        assertThat(arguments.checkout().buyer().consent()).isEqualTo(new BuyerConsentState(true, null, false, null));
        assertThat(arguments.checkout().currency()).isEqualTo("USD");
        assertThat(arguments.checkout().discounts().codes()).containsExactly("SAVE10");
        assertThat(arguments.checkout().fulfillment().methods().getFirst())
                .satisfies(method -> assertThat(method.id()).isEqualTo("method_1"));
        assertThat(arguments.checkout().attribution()).isEqualTo(attribution);
        assertThat(serializedArguments).contains(
                "\"attribution\":{\"referring_domain\":\"app.usemeant.com\","
                        + "\"utm_source\":\"meant\",\"utm_medium\":\"agentic_commerce\"}");
        assertThat(response.resolvedCheckout().id()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(response.resolvedCheckout().cartId()).isEqualTo("gid://shopify/Cart/1");
        assertThat(response.resolvedCheckout().continueUrl()).isEqualTo("https://merchant.example/continue");
        assertThat(response.resolvedCheckout().expiresAt()).isEqualTo(Instant.parse("2026-06-16T12:05:00Z"));
        assertThat(response.resolvedCheckout().currency()).isEqualTo("USD");
        assertThat(response.resolvedCheckout().discounts().applied()).isEmpty();
        assertThat(response.resolvedCheckout().ap2().merchantAuthorization()).isEqualTo("merchant-signature");
    }

    @Test
    void getBuildsTypedArgumentsAndParsesStructuredContentResponse() throws Exception {
        GetCheckoutCapability capability = new GetCheckoutCapability(objectMapper);

        GetCheckoutArguments arguments = capability.buildArguments(
                new GetCheckoutRequest("gid://shopify/Checkout/1"),
                NegotiatedCapabilities.none()
        );
        UcpCheckoutResponse response = capability.parseResponse(new UcpToolResponse(null,
                objectMapper.valueToTree(Map.of(
                        "checkout", Map.of(
                                "id", "gid://shopify/Checkout/1",
                                "continue_url", "https://merchant.example/continue"
                        ),
                        "errors", List.of()
                )),
                NegotiatedCapabilities.none()));
        String serializedArguments = objectMapper.writeValueAsString(arguments);

        assertThat(arguments.checkoutId()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(serializedArguments).contains("\"id\":\"gid://shopify/Checkout/1\"");
        assertThat(serializedArguments).doesNotContain("checkout_id");
        assertThat(response.resolvedCheckout().id()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(response.resolvedCheckout().continueUrl()).isEqualTo("https://merchant.example/continue");
    }

    @Test
    void updateBuildsBuyerEmailAndShippingArgumentsAndParsesTypedResponse() throws Exception {
        UpdateCheckoutCapability capability = new UpdateCheckoutCapability(objectMapper, attributionProperties);

        UpdateCheckoutArguments arguments = capability.buildArguments(
                new UpdateCheckoutRequest(
                        "gid://shopify/Checkout/1",
                        List.of(),
                        new CheckoutBuyer("Ada", null, null, null),
                        null,
                        "ada@example.com",
                        "USD",
                        new CheckoutContext("US", null, null, null, null, null, List.of()),
                        List.of("SAVE10"),
                        new CheckoutFulfillment(List.of(new FulfillmentMethod(
                                "method_1",
                                "shipping",
                                List.of("li_1"),
                                List.of(new ShippingDestination(
                                        "destination_1", null, "1 Main St", "New York", null,
                                        "US", null, null, null, null)),
                                "destination_1",
                                List.of()
                        )))
                ),
                NegotiatedCapabilities.none()
        );
        UcpCheckoutResponse response = capability.parseResponse(new UcpToolResponse(
                checkoutResponseJson(),
                null,
                NegotiatedCapabilities.none()
        ));
        String serializedArguments = objectMapper.writeValueAsString(arguments);

        assertThat(arguments.checkoutId()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(serializedArguments).contains("\"id\":\"gid://shopify/Checkout/1\"");
        assertThat(serializedArguments).contains("\"checkout\"");
        assertThat(serializedArguments).doesNotContain("checkout_id");
        assertThat(arguments.checkout().buyer().firstName()).isEqualTo("Ada");
        assertThat(arguments.checkout().buyer().email()).isEqualTo("ada@example.com");
        assertThat(arguments.checkout().context().addressCountry()).isEqualTo("US");
        assertThat(arguments.checkout().fulfillment().methods().getFirst())
                .satisfies(method -> assertThat(method.id()).isEqualTo("method_1"));
        assertThat(arguments.checkout().discounts().codes()).containsExactly("SAVE10");
        assertThat(arguments.checkout().attribution()).isEqualTo(attribution);
        assertThat(serializedArguments).doesNotContain("shipping_address", "available_methods");
        assertThat(response.resolvedCheckout().status()).isEqualTo("open");
    }

    @Test
    void updateDistinguishesUnchangedDiscountsFromExplicitClear() throws Exception {
        UpdateCheckoutCapability capability = new UpdateCheckoutCapability(objectMapper, attributionProperties);

        UpdateCheckoutArguments unchanged = capability.buildArguments(
                updateRequest(null), NegotiatedCapabilities.none());
        UpdateCheckoutArguments cleared = capability.buildArguments(
                updateRequest(List.of()), NegotiatedCapabilities.none());

        assertThat(objectMapper.writeValueAsString(unchanged)).doesNotContain("\"discounts\"");
        assertThat(objectMapper.writeValueAsString(cleared))
                .contains("\"discounts\":{\"codes\":[]}");
    }

    @Test
    void fulfillmentDestinationUnionParsesShippingAndRetailWireShapes() throws Exception {
        CheckoutFulfillment fulfillment = objectMapper.readValue("""
                {
                  "methods": [{
                    "id": "mixed",
                    "type": "shipping",
                    "line_item_ids": ["line-1"],
                    "destinations": [
                      {
                        "id": "home",
                        "street_address": "1 Main St",
                        "address_country": "US"
                      },
                      {
                        "id": "store-1",
                        "name": "Downtown Store",
                        "address": {"street_address": "2 Market St", "address_country": "US"}
                      }
                    ]
                  }]
                }
                """, CheckoutFulfillment.class);

        assertThat(fulfillment.methods().getFirst().destinations())
                .containsExactly(
                        new ShippingDestination(
                                "home", null, "1 Main St", null, null, "US", null, null, null, null),
                        new RetailLocation(
                                "store-1", "Downtown Store",
                                new CheckoutFulfillment.PostalAddress(
                                        null, "2 Market St", null, null, "US", null, null, null, null))
                );
        assertThat(objectMapper.writeValueAsString(fulfillment)).doesNotContain("@type", "ShippingDestination");
    }

    @Test
    void rootCheckoutResponseParsesExtensionFields() {
        CreateCheckoutCapability capability = new CreateCheckoutCapability(objectMapper, attributionProperties);

        UcpCheckoutResponse response = capability.parseResponse(new UcpToolResponse(
                """
                        {
                          "id": "checkout_456",
                          "status": "ready_for_complete",
                          "currency": "USD",
                          "context": {"address_country": "US", "language": "en-US"},
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
        assertThat(response.resolvedCheckout().context().addressCountry()).isEqualTo("US");
        assertThat(response.resolvedCheckout().context().language()).isEqualTo("en-US");
        assertThat(response.resolvedCheckout().buyer().email()).isEqualTo("jane.doe@example.com");
        assertThat(response.resolvedCheckout().discounts().codes()).containsExactly("SAVE10");
        assertThat(response.resolvedCheckout().fulfillment().methods()).isEmpty();
        assertThat(response.resolvedCheckout().ap2().merchantAuthorization()).isEqualTo("merchant-signature");
    }

    private UpdateCheckoutRequest updateRequest(List<String> discountCodes) {
        return new UpdateCheckoutRequest(
                "checkout-1", List.of(), null, null, null, "USD", null, discountCodes, null);
    }

    @Test
    void checkoutExtensionsAdvertiseDocumentedParentsWithoutStandaloneTools() {
        CapabilityAdvertisement ap2 = new Ap2MandateExtensionCapability().advertisements().getFirst();
        CapabilityAdvertisement buyerConsent = new BuyerConsentExtensionCapability().advertisements().getFirst();
        CapabilityAdvertisement discount = new DiscountExtensionCapability().advertisements().getFirst();
        CapabilityAdvertisement fulfillment = new FulfillmentExtensionCapability().advertisements().getFirst();

        assertThat(ap2.tools()).isEmpty();
        assertThat(ap2.extendsCapabilities()).containsExactly(CheckoutCapabilityMetadata.CHECKOUT);
        assertThat(ap2.config().has("vp_formats_supported")).isTrue();
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
