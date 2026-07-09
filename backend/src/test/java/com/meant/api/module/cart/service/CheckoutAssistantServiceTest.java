package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.common.service.dto.OpenRouterPlugin;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.command.AssistCheckoutCommand;
import com.meant.api.module.cart.service.command.UpdateCheckoutCommand;
import com.meant.api.module.cart.service.dto.CheckoutAssistResult;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import java.util.List;
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
    void appliesCollectedDetailsWhenAssistantIsReady() {
        CheckoutAssistantService service = service("""
                {
                  "reply": "Applying your details now.",
                  "readyToUpdate": true,
                  "buyer": {"email": "tilseroz@gmail.com", "firstName": "David", "lastName": "Tilseroz",
                    "phoneNumber": "+420731958653"},
                  "shippingAddress": {"streetAddress": "1531 Hyde St", "extendedAddress": "",
                    "addressLocality": "San Francisco", "addressRegion": "CA", "postalCode": "94109",
                    "addressCountry": "United States"}
                }
                """);

        CheckoutAssistResult result = service.assist(command(
                "Ship to 1531 Hyde St, San Francisco, CA 94109, United States, "
                        + "David Tilseroz, tilseroz@gmail.com, +420731958653"
        ));

        assertThat(result.reply()).contains(
                "I sent this to the merchant: 1531 Hyde St, San Francisco, CA, 94109, United States"
        );
        assertThat(result.reply()).contains("with contact David Tilseroz, tilseroz@gmail.com, +420731958653");
        assertThat(result.reply()).contains("Applying your details now.");
        assertThat(result.checkoutUpdated()).isTrue();
        assertThat(cartService.updateCommand).isNotNull();
        assertThat(cartService.updateCommand.buyer().email()).isEqualTo("tilseroz@gmail.com");
        assertThat(cartService.updateCommand.buyer().firstName()).isEqualTo("David");
        assertThat(cartService.updateCommand.buyer().lastName()).isEqualTo("Tilseroz");
        assertThat(cartService.updateCommand.buyer().phoneNumber()).isEqualTo("+420731958653");
        assertThat(cartService.updateCommand.shippingAddress().streetAddress()).isEqualTo("1531 Hyde St");
        assertThat(cartService.updateCommand.shippingAddress().extendedAddress()).isNull();
        assertThat(cartService.updateCommand.shippingAddress().addressCountry()).isEqualTo("US");
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
        assertThat(result.reply()).contains("Further interaction is required in the merchant checkout");
        assertThat(result.reply()).doesNotContain("An extension interaction is required");
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
        assertThat(result.reply()).contains("Send another shipping address in a supported destination");
        assertThat(result.reply()).doesNotContain("Remove them");
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
                true
        );
    }

    static class FakeCartService extends CartService {

        private UpdateCheckoutCommand updateCommand;
        private CartException updateException;
        private CheckoutResult updatedCheckout;

        FakeCartService() {
            super(null, null, null, null, null, null, null, null, null, null);
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
