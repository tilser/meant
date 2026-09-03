package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.common.service.dto.OpenRouterPlugin;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.service.command.AssistCheckoutCommand;
import com.meant.api.module.cart.service.command.UpdateCheckoutCommand;
import com.meant.api.module.cart.service.dto.CheckoutAssistResult;
import com.meant.api.module.cart.service.dto.CheckoutExecutionPlan;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.merchant.constant.CapabilityAvailability;
import com.meant.api.module.merchant.constant.CapabilityIntegrationHealth;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.dto.CapabilityAuthorizationDecision;
import com.meant.api.module.merchant.service.dto.CommerceCapabilityDecision;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationRouting;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import java.util.Arrays;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CheckoutAssistantServiceTest {

    private static final UUID CART_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private final FakeCartService cartService = new FakeCartService();

    @Test
    void returnsAssistantQuestionWithoutUpdatingWhenInformationIsMissing() {
        CheckoutAssistantService service = service("""
                {
                  "reply": "What street address should I ship to?",
                  "readyToUpdate": false,
                  "buyer": {"email": "", "firstName": "", "lastName": "", "phoneNumber": ""},
                  "shippingAddress": {"streetAddress": "", "extendedAddress": "", "addressLocality": "",
                    "addressRegion": "", "postalCode": "", "addressCountry": ""}
                }
                """);

        CheckoutAssistResult result = service.assist(command("Ship it to me in San Francisco"));

        assertThat(result.reply()).isEqualTo("What street address should I ship to?");
        assertThat(result.checkoutUpdated()).isFalse();
        assertThat(cartService.updateCommand).isNull();
    }

    @Test
    void neutralizesTransportCoordinatesReturnedByCheckoutAssistantModel() {
        CheckoutAssistantService service = service("""
                {
                  "reply": "Continue at manningshoes.myshopify.com or https://transport.example/api/ucp/mcp.",
                  "readyToUpdate": false,
                  "buyer": {"email": "", "firstName": "", "lastName": "", "phoneNumber": ""},
                  "shippingAddress": {"streetAddress": "", "extendedAddress": "", "addressLocality": "",
                    "addressRegion": "", "postalCode": "", "addressCountry": ""}
                }
                """);

        CheckoutAssistResult result = service.assist(command("What happens next?"));

        assertThat(result.reply())
                .isEqualTo("Continue at the merchant or the merchant.")
                .doesNotContain("myshopify.com", "/api/ucp/mcp");
    }

    @Test
    void appliesCollectedDetailsWhenAssistantIsReady() {
        CheckoutAssistantService service = service("""
                {
                  "reply": "Applying your details now.",
                  "readyToUpdate": true,
                  "buyer": {"email": "ada.lovelace@example.com", "firstName": "Ada", "lastName": "Lovelace",
                    "phoneNumber": "+1 202 555 0147"},
                  "shippingAddress": {"streetAddress": "1531 Hyde St", "extendedAddress": "",
                    "addressLocality": "San Francisco", "addressRegion": "CA", "postalCode": "94109",
                    "addressCountry": "United States"}
                }
                """);

        CheckoutAssistResult result = service.assist(command(
                "Ship to 1531 Hyde St, San Francisco, CA 94109, United States, "
                        + "Ada Lovelace, ada.lovelace@example.com, +1 202 555 0147"
        ));

        assertThat(result.reply()).contains(
                "I sent this to the merchant: 1531 Hyde St, San Francisco, CA, 94109, United States"
        );
        assertThat(result.reply()).contains("with contact Ada Lovelace, ada.lovelace@example.com, +1 202 555 0147");
        assertThat(result.reply()).contains("Applying your details now.");
        assertThat(result.checkoutUpdated()).isTrue();
        assertThat(cartService.updateCommand).isNotNull();
        assertThat(cartService.updateCommand.buyer().email()).isEqualTo("ada.lovelace@example.com");
        assertThat(cartService.updateCommand.buyer().firstName()).isEqualTo("Ada");
        assertThat(cartService.updateCommand.buyer().lastName()).isEqualTo("Lovelace");
        assertThat(cartService.updateCommand.buyer().phoneNumber()).isEqualTo("+1 202 555 0147");
        assertThat(cartService.updateCommand.shippingAddress().streetAddress()).isEqualTo("1531 Hyde St");
        assertThat(cartService.updateCommand.shippingAddress().extendedAddress()).isNull();
        assertThat(cartService.updateCommand.shippingAddress().addressCountry()).isEqualTo("US");
    }

    @Test
    void sanitizesUcpTransportEndpointBeforeBuildingCheckoutAssistantReply() throws Exception {
        String endpoint = "https://weareallbirds.myshopify.com/api/ucp/mcp";
        String advertisedEndpoint =
                "https://advertised.shopify-transport.example/api/ucp/mcp";
        String profileEndpoint =
                "https://profile.shopify-transport.example/.well-known/ucp";
        String integrationEndpoint =
                "https://integration.shopify-transport.example/custom/checkout";
        Cart cart = Cart.builder()
                .id(CART_ID)
                .merchantDomain("allbirds.com")
                .routingDomain("weareallbirds.myshopify.com")
                .endpoint(endpoint)
                .remoteCartId("gid://shopify/Cart/1")
                .remoteCartIdHash("hash")
                .rawCartResponse("{}")
                .totalQuantity(1)
                .build();
        ObjectMapper objectMapper = new ObjectMapper();
        UcpCheckoutResponse response = objectMapper.readValue("""
                {
                  "checkout": {
                    "id": "gid://shopify/Checkout/1",
                    "cart_id": "gid://shopify/Cart/1",
                    "status": "incomplete",
                    "checkout_url": "https://allbirds.com/checkouts/1",
                    "messages": [
                      {
                        "type": "warning",
                        "code": "merchant_notice",
                        "severity": "recoverable",
                        "content": "Continue at https://advertised.shopify-transport.example/api/ucp/mcp. Profile: profile.shopify-transport.example. Product: https://integration.shopify-transport.example/products/tree-runner"
                      }
                    ]
                  },
                  "messages": [],
                  "errors": []
                }
                """, UcpCheckoutResponse.class);
        MerchantCartProvider provider = new MerchantCartProvider(
                UUID.randomUUID(),
                "allbirds.com",
                "weareallbirds.myshopify.com",
                advertisedEndpoint,
                profileEndpoint,
                List.of(new MerchantIntegrationRouting(
                        UUID.randomUUID(),
                        MerchantIntegrationProvider.SHOPIFY,
                        Set.of(MerchantIntegrationRole.CHECKOUT),
                        MerchantIntegrationStatus.ACTIVE,
                        "gid://shopify/Shop/1",
                        "integration.shopify-transport.example",
                        "gid://shopify/Shop/1",
                        integrationEndpoint
                )),
                embeddedCheckoutPolicy(),
                null,
                Set.of("dev.ucp.shopping.checkout")
        );
        cartService.updatedCheckout = new CheckoutResultMapper(
                objectMapper,
                new CheckoutExecutionPlanner()
        ).from(cart, response, provider);
        CheckoutAssistantService service = service("""
                {
                  "reply": "Applying your details now.",
                  "readyToUpdate": true,
                  "buyer": {"email": "ada@example.com", "firstName": "Ada", "lastName": "Lovelace",
                    "phoneNumber": "+14155551234"},
                  "shippingAddress": {"streetAddress": "1531 Hyde St", "extendedAddress": "",
                    "addressLocality": "San Francisco", "addressRegion": "CA", "postalCode": "94109",
                    "addressCountry": "US"}
                }
                """);

        CheckoutAssistResult result = service.assist(command("Use my shipping details"));

        assertThat(result.reply())
                .contains("Merchant response: Continue at allbirds.com")
                .contains("Profile: allbirds.com")
                .contains("https://allbirds.com/products/tree-runner")
                .doesNotContain(
                        "myshopify.com",
                        "advertised.shopify-transport.example",
                        "profile.shopify-transport.example",
                        "integration.shopify-transport.example",
                        "/api/ucp/mcp"
                );
    }

    @Test
    void reportsRequiredMerchantInteractionAfterCheckoutDetailsAreAccepted() {
        cartService.updatedCheckout = checkoutResult(
                "requires_escalation",
                List.of(new CheckoutResult.Message(
                        "error",
                        "extension_interaction_required",
                        "requires_buyer_input",
                        "An extension interaction is required to complete the checkout.",
                        null
                ))
        );
        CheckoutAssistantService service = service("""
                {
                  "reply": "Applying your details now.",
                  "readyToUpdate": true,
                  "buyer": {"email": "ada@example.com", "firstName": "Ada", "lastName": "Lovelace",
                    "phoneNumber": "+14155551234"},
                  "shippingAddress": {"streetAddress": "1531 Hyde St", "extendedAddress": "",
                    "addressLocality": "San Francisco", "addressRegion": "CA", "postalCode": "94109",
                    "addressCountry": "US"}
                }
                """);

        CheckoutAssistResult result = service.assist(command("Use my shipping details"));

        assertThat(result.checkoutUpdated()).isTrue();
        assertThat(result.reply()).contains("The merchant accepted those checkout details.");
        assertThat(result.reply()).contains("secure embedded checkout below")
                .contains("finish inside Meant");
        assertThat(result.reply()).doesNotContain("An extension interaction is required");
    }

    @Test
    void surfacesExactShippingRejectionWhenMerchantAlsoReturnsAnotherRecoverableMessage() {
        cartService.updatedCheckout = checkoutResult(
                "incomplete",
                List.of(
                        new CheckoutResult.Message(
                                "error",
                                "delivery_phone_number_required",
                                "recoverable",
                                "Enter a phone number to use this delivery method",
                                null
                        ),
                        new CheckoutResult.Message(
                                "error",
                                "delivery_no_delivery_available_for_merchandise_line",
                                "recoverable",
                                "Your cart has been updated and the items you added can’t be shipped to your address.",
                                null
                        )
                )
        );
        CheckoutAssistantService service = service("""
                {
                  "reply": "Applying your details now.",
                  "readyToUpdate": true,
                  "buyer": {"email": "buyer@example.com", "firstName": "Buyer", "lastName": "Test",
                    "phoneNumber": "+420731958654"},
                  "shippingAddress": {"streetAddress": "1531 Hyde St", "extendedAddress": "",
                    "addressLocality": "San Francisco", "addressRegion": "CA", "postalCode": "94109",
                    "addressCountry": "US"}
                }
                """);

        CheckoutAssistResult result = service.assist(command("Use this address"));

        assertThat(result.checkoutUpdated()).isTrue();
        assertThat(result.reply())
                .contains("Merchant response: Your cart has been updated")
                .contains("can’t be shipped to your address")
                .contains("Choose another merchant offer for this destination")
                .doesNotContain("has not returned supported shipping destinations");
    }

    @Test
    void normalizesCountryAndRegionNamesBeforeUpdatingMerchant() {
        CheckoutAssistantService service = service("""
                {
                  "reply": "Applying your details now.",
                  "readyToUpdate": true,
                  "buyer": {"email": "ada@example.com", "firstName": "Ada", "lastName": "Lovelace",
                    "phoneNumber": "+14155551234"},
                  "shippingAddress": {"streetAddress": "1531 Hyde St", "extendedAddress": "",
                    "addressLocality": "San Francisco", "addressRegion": "California", "postalCode": "94109",
                    "addressCountry": "United States"}
                }
                """);

        CheckoutAssistResult result = service.assist(command("San Francisco, California, United States"));

        assertThat(result.checkoutUpdated()).isTrue();
        assertThat(cartService.updateCommand.shippingAddress().addressRegion()).isEqualTo("CA");
        assertThat(cartService.updateCommand.shippingAddress().addressCountry()).isEqualTo("US");
    }

    @Test
    void reportsSubmittedDetailsAndRetryGuidanceWhenUpdateDestinationIsRejected() {
        cartService.updateException = new CartException(
                "The merchant says these items cannot be shipped to that destination. Remove them."
        );
        CheckoutAssistantService service = service("""
                {
                  "reply": "Applying your details now.",
                  "readyToUpdate": true,
                  "buyer": {"email": "ada@example.com", "firstName": "Ada", "lastName": "Lovelace",
                    "phoneNumber": "+14155551234"},
                  "shippingAddress": {"streetAddress": "1531 Hyde St", "extendedAddress": "",
                    "addressLocality": "San Francisco", "addressRegion": "CA", "postalCode": "94109",
                    "addressCountry": "us"}
                }
                """);

        CheckoutAssistResult result = service.assist(commandWithDeliveryHint("94109, country US"));

        assertThat(result.checkoutUpdated()).isFalse();
        assertThat(result.reply()).contains(
                "I sent this to the merchant: 1531 Hyde St, San Francisco, CA, 94109, us"
        );
        assertThat(result.reply()).contains("The merchant rejected that shipping destination.");
        assertThat(result.reply()).contains("Known delivery coverage: ships to United States and Canada.");
        assertThat(result.reply()).contains("Merchant response:");
        assertThat(result.reply()).contains("Remove them");
        assertThat(result.reply()).contains("Choose another merchant offer for this destination");
    }

    @Test
    void ignoresReadyFlagWhenRequiredFieldsAreBlank() {
        CheckoutAssistantService service = service("""
                {
                  "reply": "Almost there.",
                  "readyToUpdate": true,
                  "buyer": {"email": "ada@example.com", "firstName": "Ada", "lastName": "", "phoneNumber": ""},
                  "shippingAddress": {"streetAddress": "1531 Hyde St", "extendedAddress": "",
                    "addressLocality": "San Francisco", "addressRegion": "CA", "postalCode": "94109",
                    "addressCountry": "US"}
                }
                """);

        CheckoutAssistResult result = service.assist(command("my email is ada@example.com"));

        assertThat(result.checkoutUpdated()).isFalse();
        assertThat(cartService.updateCommand).isNull();
    }

    @Test
    void appliesParsedFollowUpWhenAssistantAskedForLastName() {
        CheckoutAssistantService service = service("""
                {
                  "reply": "Applying your details now.",
                  "readyToUpdate": true,
                  "buyer": {"email": "ada@example.com", "firstName": "Ada", "lastName": "Lovelace",
                    "phoneNumber": ""},
                  "shippingAddress": {"streetAddress": "1531 Hyde St", "extendedAddress": "",
                    "addressLocality": "San Francisco", "addressRegion": "CA", "postalCode": "94109",
                    "addressCountry": "US"}
                }
                """);

        CheckoutAssistResult result = service.assist(new AssistCheckoutCommand(
                CART_ID,
                USER_ID,
                "Lovelace",
                null,
                List.of(
                        new AssistCheckoutCommand.HistoryMessage(
                                "user",
                                "Ship to 1531 Hyde St, San Francisco, CA 94109, US, Ada, ada@example.com"
                        ),
                        new AssistCheckoutCommand.HistoryMessage(
                                "assistant",
                                "I need your last name to complete the checkout."
                        )
                )
        ));

        assertThat(result.checkoutUpdated()).isTrue();
        assertThat(cartService.updateCommand).isNotNull();
        assertThat(cartService.updateCommand.buyer().lastName()).isEqualTo("Lovelace");
        assertThat(result.reply()).contains("Ada Lovelace");
    }

    @Test
    void waitsForRegionWhenShippingToUsOrCanada() {
        CheckoutAssistantService service = service("""
                {
                  "reply": "What state should I use?",
                  "readyToUpdate": true,
                  "buyer": {"email": "ada@example.com", "firstName": "Ada", "lastName": "Lovelace",
                    "phoneNumber": ""},
                  "shippingAddress": {"streetAddress": "1531 Hyde St", "extendedAddress": "",
                    "addressLocality": "San Francisco", "addressRegion": "", "postalCode": "94109",
                    "addressCountry": "US"}
                }
                """);

        CheckoutAssistResult result = service.assist(command("ship to San Francisco"));

        assertThat(result.checkoutUpdated()).isFalse();
        assertThat(cartService.updateCommand).isNull();
    }

    @Test
    void fallsBackWhenAssistantModelFails() {
        CheckoutAssistantService service = new CheckoutAssistantService(
                cartService,
                new FailingChatClient(),
                properties(),
                new ObjectMapper()
        );

        CheckoutAssistResult result = service.assist(command("hello"));

        assertThat(result.reply()).contains("send the shipping and contact details here");
        assertThat(result.checkoutUpdated()).isFalse();
    }

    private CheckoutAssistantService service(String modelResponse) {
        return new CheckoutAssistantService(
                cartService,
                new StubChatClient(modelResponse),
                properties(),
                new ObjectMapper()
        );
    }

    private AssistCheckoutCommand command(String message) {
        return new AssistCheckoutCommand(
                CART_ID,
                USER_ID,
                message,
                null,
                List.of(new AssistCheckoutCommand.HistoryMessage("assistant", "What is your address?"))
        );
    }

    private AssistCheckoutCommand commandWithDeliveryHint(String message) {
        return new AssistCheckoutCommand(
                CART_ID,
                USER_ID,
                message,
                "Known delivery coverage: ships to United States and Canada.",
                List.of(new AssistCheckoutCommand.HistoryMessage("assistant", "What is your address?"))
        );
    }

    private OpenRouterProperties properties() {
        return new OpenRouterProperties(
                "https://openrouter.example",
                "key",
                "Meant",
                new OpenRouterProperties.Models("m", "m", "m", "chat-model-test")
        );
    }

    private static CheckoutResult checkoutResult(String status) {
        return checkoutResult(
                status,
                List.of(new CheckoutResult.Message(
                        "error",
                        "delivery_address_required",
                        "recoverable",
                        "A destination address is required in order to continue.",
                        null
                ))
        );
    }

    private static CheckoutResult checkoutResult(String status, List<CheckoutResult.Message> messages) {
        MerchantExecutionPolicy policy = embeddedCheckoutPolicy();
        CheckoutExecutionPlan execution = new CheckoutExecutionPlanner().resolve(status, messages, policy);
        return new CheckoutResult(
                CART_ID,
                "gid://shopify/Cart/1",
                "gid://shopify/Checkout/1",
                status,
                null,
                "https://merchant.example/continue",
                "2026-04-08",
                5200L,
                "USD",
                messages,
                execution.nextAction(),
                execution.selectedRail(),
                execution.ineligibilityReasons(),
                policy
        );
    }

    private static MerchantExecutionPolicy embeddedCheckoutPolicy() {
        return new MerchantExecutionPolicy(Arrays.stream(CommerceOperation.values())
                .map(operation -> operation == CommerceOperation.EMBEDDED_CHECKOUT
                        ? new CommerceCapabilityDecision(
                                operation,
                                true,
                                CapabilityAuthorizationDecision.notRequired(),
                                true,
                                CapabilityIntegrationHealth.HEALTHY,
                                true,
                                CapabilityAvailability.AVAILABLE,
                                CommerceExecutionRail.EMBEDDED_CHECKOUT,
                                List.of(),
                                UUID.randomUUID(),
                                null
                        )
                        : MerchantExecutionPolicy.unavailable().decision(operation))
                .toList());
    }

    static class FakeCartService extends CartService {

        private UpdateCheckoutCommand updateCommand;
        private CartException updateException;
        private CheckoutResult updatedCheckout;

        FakeCartService() {
            super(
                    mock(com.meant.api.module.merchant.service.MerchantCartProviderLookupService.class),
                    mock(CartBuyerContextService.class), mock(CartPersistenceService.class),
                    mock(MerchantCartPluginDispatchService.class),
                    mock(com.meant.api.module.checkout.service.MerchantCheckoutPluginDispatchService.class),
                    mock(com.meant.api.module.checkout.service.NativeCheckoutCompletionService.class),
                    mock(com.meant.api.module.checkout.service.CheckoutPurchaseAttributionService.class),
                    mock(CartResultMapper.class), mock(CheckoutResultMapper.class),
                    mock(CartCheckoutConsentService.class),
                    mock(com.meant.api.module.user.service.UserSelectedOfferResolutionService.class),
                    mock(SelectedOfferCartRoutingService.class), mock(CartOfferRevalidationService.class),
                    new CartBindingMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                    mock(com.meant.api.module.user.service.UserCommerceContextService.class),
                    new CartReplacementService(
                            new CartLineOfferIdentityMapper(new tools.jackson.databind.ObjectMapper()),
                            new CartFulfillmentReplacementService()),
                    new CommerceMutationPolicy(
                            new com.meant.api.module.cart.properties.CartRetryProperties(java.time.Duration.ofSeconds(2)),
                            new CartRetrySleeper()),
                    new CheckoutUpdateReconciliationService(), new CheckoutCancellationPolicy(),
                    mock(com.meant.api.module.user.service.UserCheckoutDetailsService.class),
                    new com.meant.api.common.service.UserMutationExecutionLane());
        }

        @Override
        public CheckoutResult checkout(GetCheckoutQuery query) {
            return checkoutResult("requires_escalation");
        }

        @Override
        public CheckoutResult updateCheckout(UpdateCheckoutCommand command) {
            if (updateException != null) {
                throw updateException;
            }
            updateCommand = command;
            return updatedCheckout == null
                    ? checkoutResult("ready_for_complete", List.of())
                    : updatedCheckout;
        }
    }

    static class StubChatClient extends OpenRouterChatClient {

        private final String response;

        StubChatClient(String response) {
            super(null, null);
            this.response = response;
        }

        @Override
        public String completeJson(
                String model,
                String systemPrompt,
                String userPrompt,
                String schemaName,
                OpenRouterJsonSchemaDefinition schema,
                List<OpenRouterPlugin> plugins
        ) {
            return response;
        }
    }

    static class FailingChatClient extends OpenRouterChatClient {

        FailingChatClient() {
            super(null, null);
        }

        @Override
        public String completeJson(
                String model,
                String systemPrompt,
                String userPrompt,
                String schemaName,
                OpenRouterJsonSchemaDefinition schema,
                List<OpenRouterPlugin> plugins
        ) {
            throw new OpenRouterException("model unavailable");
        }
    }
}
