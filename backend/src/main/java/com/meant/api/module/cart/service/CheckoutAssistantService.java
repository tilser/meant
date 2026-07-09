package com.meant.api.module.cart.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.OpenRouterJsonExtractor;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.command.AssistCheckoutCommand;
import com.meant.api.module.cart.service.command.UpdateCheckoutCommand;
import com.meant.api.module.cart.service.dto.CheckoutAssistResult;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Conversational checkout helper: asks the buyer for the pieces the merchant still needs
 * (buyer identity + shipping destination) and applies them to the active UCP checkout
 * session through the same update path the address form uses.
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class CheckoutAssistantService {

    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_HISTORY_MESSAGE_LENGTH = 1000;
    private static final String FALLBACK_REPLY =
            "I could not reach the checkout assistant right now. "
                    + "Please send the shipping and contact details here again.";
    private static final Map<String, String> COUNTRY_ALIASES = Map.ofEntries(
            Map.entry("UNITED STATES", "US"),
            Map.entry("UNITED STATES OF AMERICA", "US"),
            Map.entry("USA", "US"),
            Map.entry("U.S.", "US"),
            Map.entry("U.S.A.", "US"),
            Map.entry("AMERICA", "US"),
            Map.entry("CANADA", "CA"),
            Map.entry("UNITED KINGDOM", "GB"),
            Map.entry("UK", "GB"),
            Map.entry("GREAT BRITAIN", "GB"),
            Map.entry("BRITAIN", "GB"),
            Map.entry("ENGLAND", "GB"),
            Map.entry("GERMANY", "DE"),
            Map.entry("DEUTSCHLAND", "DE"),
            Map.entry("AUSTRALIA", "AU"),
            Map.entry("CZECH REPUBLIC", "CZ"),
            Map.entry("CZECHIA", "CZ"),
            Map.entry("CESKO", "CZ"),
            Map.entry("\u010cESKO", "CZ")
    );
    private static final Map<String, String> US_REGION_ALIASES = Map.ofEntries(
            Map.entry("ALABAMA", "AL"),
            Map.entry("ALASKA", "AK"),
            Map.entry("ARIZONA", "AZ"),
            Map.entry("ARKANSAS", "AR"),
            Map.entry("CALIFORNIA", "CA"),
            Map.entry("COLORADO", "CO"),
            Map.entry("CONNECTICUT", "CT"),
            Map.entry("DELAWARE", "DE"),
            Map.entry("DISTRICT OF COLUMBIA", "DC"),
            Map.entry("FLORIDA", "FL"),
            Map.entry("GEORGIA", "GA"),
            Map.entry("HAWAII", "HI"),
            Map.entry("IDAHO", "ID"),
            Map.entry("ILLINOIS", "IL"),
            Map.entry("INDIANA", "IN"),
            Map.entry("IOWA", "IA"),
            Map.entry("KANSAS", "KS"),
            Map.entry("KENTUCKY", "KY"),
            Map.entry("LOUISIANA", "LA"),
            Map.entry("MAINE", "ME"),
            Map.entry("MARYLAND", "MD"),
            Map.entry("MASSACHUSETTS", "MA"),
            Map.entry("MICHIGAN", "MI"),
            Map.entry("MINNESOTA", "MN"),
            Map.entry("MISSISSIPPI", "MS"),
            Map.entry("MISSOURI", "MO"),
            Map.entry("MONTANA", "MT"),
            Map.entry("NEBRASKA", "NE"),
            Map.entry("NEVADA", "NV"),
            Map.entry("NEW HAMPSHIRE", "NH"),
            Map.entry("NEW JERSEY", "NJ"),
            Map.entry("NEW MEXICO", "NM"),
            Map.entry("NEW YORK", "NY"),
            Map.entry("NORTH CAROLINA", "NC"),
            Map.entry("NORTH DAKOTA", "ND"),
            Map.entry("OHIO", "OH"),
            Map.entry("OKLAHOMA", "OK"),
            Map.entry("OREGON", "OR"),
            Map.entry("PENNSYLVANIA", "PA"),
            Map.entry("RHODE ISLAND", "RI"),
            Map.entry("SOUTH CAROLINA", "SC"),
            Map.entry("SOUTH DAKOTA", "SD"),
            Map.entry("TENNESSEE", "TN"),
            Map.entry("TEXAS", "TX"),
            Map.entry("UTAH", "UT"),
            Map.entry("VERMONT", "VT"),
            Map.entry("VIRGINIA", "VA"),
            Map.entry("WASHINGTON", "WA"),
            Map.entry("WEST VIRGINIA", "WV"),
            Map.entry("WISCONSIN", "WI"),
            Map.entry("WYOMING", "WY")
    );
    private static final Map<String, String> CA_REGION_ALIASES = Map.ofEntries(
            Map.entry("ALBERTA", "AB"),
            Map.entry("BRITISH COLUMBIA", "BC"),
            Map.entry("MANITOBA", "MB"),
            Map.entry("NEW BRUNSWICK", "NB"),
            Map.entry("NEWFOUNDLAND AND LABRADOR", "NL"),
            Map.entry("NOVA SCOTIA", "NS"),
            Map.entry("ONTARIO", "ON"),
            Map.entry("PRINCE EDWARD ISLAND", "PE"),
            Map.entry("QUEBEC", "QC"),
            Map.entry("QU\u00c9BEC", "QC"),
            Map.entry("SASKATCHEWAN", "SK")
    );

    private final CartService cartService;
    private final OpenRouterChatClient openRouterChatClient;
    private final OpenRouterProperties openRouterProperties;
    private final ObjectMapper objectMapper;

    public CheckoutAssistResult assist(@NotNull @Valid AssistCheckoutCommand command) {
        CheckoutResult checkout = cartService.checkout(
                new GetCheckoutQuery(command.cartId(), command.userId(), false)
        );

        AssistantTurn turn;
        try {
            String response = openRouterChatClient.completeJson(
                    openRouterProperties.models().chatModel(),
                    systemPrompt(),
                    userPrompt(command, checkout),
                    "checkout_detail_parse",
                    assistSchema()
            );
            turn = parseTurn(response);
        } catch (OpenRouterException | JacksonException exception) {
            log.warn("Checkout assistant completion failed ({})", exception.getClass().getSimpleName());
            return new CheckoutAssistResult(FALLBACK_REPLY, false, checkout);
        }

        UpdateCheckoutCommand update = updateCommand(command, turn);
        if (update == null) {
            return new CheckoutAssistResult(turn.reply(), false, checkout);
        }
        try {
            CheckoutResult updated = cartService.updateCheckout(update);
            return new CheckoutAssistResult(appliedReply(command, turn, updated), true, updated);
        } catch (CartException exception) {
            return new CheckoutAssistResult(
                    rejectedReply(command, turn, exception),
                    false,
                    checkout
            );
        }
    }

    private String appliedReply(AssistCheckoutCommand command, AssistantTurn turn, CheckoutResult updated) {
        String submitted = submittedDetails(turn);
        CheckoutResult.Message merchantMessage = firstBuyerRelevantMessage(updated);
        String merchantMessageText = merchantMessage == null ? null : merchantMessage.content();
        StringBuilder reply = new StringBuilder();
        if (hasText(submitted)) {
            reply.append("I sent this to the merchant: ").append(submitted).append(". ");
        }
        String contactRejection = contactRejectionReply(merchantMessage);
        if (contactRejection != null) {
            reply.append(contactRejection);
            return reply.toString();
        }
        if (isShippingRejection(merchantMessage)) {
            reply.append(destinationRejectedReply(command.merchantDeliveryHint(), merchantMessageText));
            return reply.toString();
        }
        if (hasText(merchantMessageText)) {
            reply.append("Merchant response: ").append(merchantMessageText);
            return reply.toString();
        }
        reply.append(hasText(turn.reply()) ? turn.reply().trim() : "The merchant accepted those checkout details.");
        return reply.toString();
    }

    /**
     * Merchant validation messages arrive in the shop's language, so field-level contact
     * rejections are recognized by their UCP message code and answered in a way the LLM and
     * buyer can act on.
     */
    private String contactRejectionReply(CheckoutResult.Message message) {
        String code = message == null || message.code() == null
                ? ""
                : message.code().trim().toLowerCase(Locale.ROOT);
        if (!code.startsWith("buyer_identity")) {
            return null;
        }
        String detail = hasText(message.content()) ? " Merchant response: " + message.content().trim() : "";
        if (code.contains("email")) {
            return "The merchant rejected the email address as invalid — please send a real, "
                    + "deliverable email address." + detail;
        }
        if (code.contains("phone")) {
            return "The merchant rejected the phone number — please send it in international "
                    + "format (e.g. +1 415 555 0100)." + detail;
        }
        return "The merchant rejected the contact details — please double-check them and send "
                + "them again." + detail;
    }

    private String rejectedReply(AssistCheckoutCommand command, AssistantTurn turn, CartException exception) {
        String submitted = submittedDetails(turn);
        StringBuilder reply = new StringBuilder();
        if (hasText(submitted)) {
            reply.append("I sent this to the merchant: ").append(submitted).append(". ");
        }
        if (isShippingRejection(exception.getMessage())) {
            reply.append(destinationRejectedReply(command.merchantDeliveryHint(), exception.getMessage()));
            return reply.toString();
        }
        reply.append("Merchant response: ").append(exception.getMessage());
        return reply.toString();
    }

    private String destinationRejectedReply(String merchantDeliveryHint, String merchantMessage) {
        StringBuilder reply = new StringBuilder("The merchant rejected that shipping destination.");
        if (hasText(merchantDeliveryHint)) {
            reply.append(' ').append(merchantDeliveryHint.trim());
        } else {
            reply.append(" The merchant did not return a supported-destinations list through checkout, ")
                    .append("so I only know this address was rejected.");
        }
        if (hasText(merchantMessage) && !merchantMessage.toLowerCase(Locale.ROOT).contains("remove")) {
            reply.append(" Merchant response: ").append(merchantMessage.trim());
        }
        reply.append(" Send another shipping address in a supported destination and I will retry.");
        return reply.toString();
    }

    private String submittedDetails(AssistantTurn turn) {
        if (turn == null || turn.shippingAddress() == null || turn.buyer() == null) {
            return null;
        }
        AssistantAddress address = turn.shippingAddress();
        AssistantBuyer buyer = turn.buyer();
        List<String> destination = java.util.stream.Stream.of(
                address.streetAddress(),
                address.extendedAddress(),
                address.addressLocality(),
                address.addressRegion(),
                address.postalCode(),
                address.addressCountry()
        )
                .filter(this::hasText)
                .map(String::trim)
                .toList();
        String buyerName = java.util.stream.Stream.of(
                buyer.firstName(),
                buyer.lastName()
        )
                .filter(this::hasText)
                .map(String::trim)
                .reduce((first, second) -> first + " " + second)
                .orElse(null);
        List<String> contact = java.util.stream.Stream.of(
                buyerName,
                buyer.email(),
                buyer.phoneNumber()
        )
                .filter(this::hasText)
                .map(String::trim)
                .toList();
        if (destination.isEmpty() && contact.isEmpty()) {
            return null;
        }
        if (contact.isEmpty()) {
            return String.join(", ", destination);
        }
        if (destination.isEmpty()) {
            return String.join(", ", contact);
        }
        return String.join(", ", destination) + " with contact " + String.join(", ", contact);
    }

    private CheckoutResult.Message firstBuyerRelevantMessage(CheckoutResult checkout) {
        return checkout.messages().stream()
                .filter(message -> hasText(message.content()))
                .filter(message -> !"extension_interaction_required".equalsIgnoreCase(
                        message.code() == null ? "" : message.code().trim()
                ))
                .findFirst()
                .orElse(null);
    }

    private UpdateCheckoutCommand updateCommand(AssistCheckoutCommand command, AssistantTurn turn) {
        if (turn == null || !turn.readyToUpdate() || !hasRequiredCheckoutDetails(turn)) {
            return null;
        }
        AssistantBuyer buyer = turn.buyer();
        AssistantAddress address = turn.shippingAddress();
        String countryCode = normalizedCountryCode(address.addressCountry());
        String regionCode = normalizedRegionCode(countryCode, address.addressRegion());
        return new UpdateCheckoutCommand(
                command.cartId(),
                command.userId(),
                new UpdateCheckoutCommand.Buyer(
                        buyer.email().trim(),
                        buyer.firstName().trim(),
                        buyer.lastName().trim(),
                        trimmedOrNull(buyer.phoneNumber())
                ),
                new UpdateCheckoutCommand.PostalAddress(
                        address.streetAddress().trim(),
                        trimmedOrNull(address.extendedAddress()),
                        address.addressLocality().trim(),
                        trimmedOrNull(regionCode),
                        address.postalCode().trim(),
                        countryCode
                ),
                null
        );
    }

    private boolean hasRequiredCheckoutDetails(AssistantTurn turn) {
        if (turn == null || turn.buyer() == null || turn.shippingAddress() == null) {
            return false;
        }
        AssistantBuyer buyer = turn.buyer();
        AssistantAddress address = turn.shippingAddress();
        String countryCode = normalizedCountryCode(address.addressCountry());
        String regionCode = normalizedRegionCode(countryCode, address.addressRegion());
        return hasText(buyer.email())
                && hasText(buyer.firstName())
                && hasText(buyer.lastName())
                && hasText(address.streetAddress())
                && hasText(address.addressLocality())
                && hasText(address.postalCode())
                && hasText(countryCode)
                && (!requiresRegion(countryCode) || hasText(regionCode));
    }

    private String normalizedCountryCode(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace('.', ' ').replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (normalized.length() == 2) {
            return normalized;
        }
        return COUNTRY_ALIASES.get(normalized);
    }

    private String normalizedRegionCode(String countryCode, String value) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        String normalized = trimmed.replace('.', ' ').replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (normalized.length() == 2) {
            return normalized;
        }
        if ("US".equalsIgnoreCase(countryCode)) {
            return US_REGION_ALIASES.getOrDefault(normalized, trimmed);
        }
        if ("CA".equalsIgnoreCase(countryCode)) {
            return CA_REGION_ALIASES.getOrDefault(normalized, trimmed);
        }
        return trimmed;
    }

    private AssistantTurn parseTurn(String response) throws JacksonException {
        return objectMapper.readValue(
                OpenRouterJsonExtractor.objectCandidate(response),
                AssistantTurn.class
        );
    }

    private String systemPrompt() {
        return """
                You are Meant's checkout detail parser for an in-chat merchant checkout.
                Return only the JSON object required by the schema.

                Your job is to parse buyer-provided free text into:
                - buyer.email
                - buyer.firstName
                - buyer.lastName
                - buyer.phoneNumber, optional
                - shippingAddress.streetAddress
                - shippingAddress.extendedAddress, optional
                - shippingAddress.addressLocality
                - shippingAddress.addressRegion, required for US and CA
                - shippingAddress.postalCode
                - shippingAddress.addressCountry as a 2-letter country code when clear

                The buyer is shown this template and often sends it in one message:
                "Ship to 1531 Hyde St, San Francisco, CA 94109, US, David Test, \
                david@test.cz, +420 731 958 653".

                Parsing rules:
                - The current buyer message is the highest priority source. Merchant messages \
                may describe stale checkout state from before the current buyer message; never \
                let a merchant "address required" message override an address the buyer just sent.
                - Parse the whole conversation, not only the latest answer. Carry forward all \
                details the buyer already gave in earlier user turns.
                - If the buyer sends the template form, split it into destination, full name, \
                email, and phone. In "1531 Hyde St, San Francisco, CA 94109, US", parse \
                streetAddress=1531 Hyde St, addressLocality=San Francisco, \
                addressRegion=CA, postalCode=94109, addressCountry=US.
                - If the buyer later answers one missing field, merge that answer with details \
                from earlier buyer turns.
                - Never invent values. If you are not sure which field a value belongs to, leave \
                that field empty and ask one concise clarifying question in reply.
                - Use empty strings for unknown fields.
                - Set readyToUpdate true only when every required field is known. Otherwise set \
                readyToUpdate false and ask for at most two missing things.
                - Reply in the language the buyer writes in.
                - If a merchant message says the items cannot be shipped, do not tell the buyer \
                to remove items. Say the destination was rejected, use known delivery coverage \
                if present, and ask for another supported shipping address.
                """;
    }

    private String userPrompt(AssistCheckoutCommand command, CheckoutResult checkout) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Current buyer message to parse first:\n");
        prompt.append(truncate(command.message())).append("\n\n");
        prompt.append("Prior conversation turns, oldest first:\n");
        List<AssistCheckoutCommand.HistoryMessage> history = command.history();
        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (AssistCheckoutCommand.HistoryMessage message : history.subList(start, history.size())) {
            prompt.append(message.role()).append(": ").append(truncate(message.content())).append('\n');
        }
        prompt.append("\nCheckout state for context:\n");
        prompt.append("status: ").append(checkout.status() == null ? "unknown" : checkout.status()).append('\n');
        if (!checkout.messages().isEmpty()) {
            prompt.append("merchant messages, possibly stale relative to current buyer message:\n");
            checkout.messages().stream()
                    .limit(8)
                    .forEach(message -> prompt.append("- ")
                            .append(message.code() == null ? "" : message.code() + ": ")
                            .append(message.content() == null ? "" : message.content())
                            .append(" (severity: ")
                            .append(message.severity() == null ? "unknown" : message.severity())
                            .append(")\n"));
        }
        if (hasText(command.merchantDeliveryHint())) {
            prompt.append("known merchant delivery coverage: ")
                    .append(truncate(command.merchantDeliveryHint()))
                    .append('\n');
        }
        return prompt.toString();
    }

    private OpenRouterJsonSchemaDefinition assistSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("reply", "readyToUpdate", "buyer", "shippingAddress"),
                Map.of(
                        "reply", OpenRouterJsonSchemaDefinition.string(),
                        "readyToUpdate", OpenRouterJsonSchemaDefinition.bool(),
                        "buyer", OpenRouterJsonSchemaDefinition.object(
                                List.of("email", "firstName", "lastName", "phoneNumber"),
                                Map.of(
                                        "email", OpenRouterJsonSchemaDefinition.string(),
                                        "firstName", OpenRouterJsonSchemaDefinition.string(),
                                        "lastName", OpenRouterJsonSchemaDefinition.string(),
                                        "phoneNumber", OpenRouterJsonSchemaDefinition.string()
                                )
                        ),
                        "shippingAddress", OpenRouterJsonSchemaDefinition.object(
                                List.of(
                                        "streetAddress",
                                        "extendedAddress",
                                        "addressLocality",
                                        "addressRegion",
                                        "postalCode",
                                        "addressCountry"
                                ),
                                Map.of(
                                        "streetAddress", OpenRouterJsonSchemaDefinition.string(),
                                        "extendedAddress", OpenRouterJsonSchemaDefinition.string(),
                                        "addressLocality", OpenRouterJsonSchemaDefinition.string(),
                                        "addressRegion", OpenRouterJsonSchemaDefinition.string(),
                                        "postalCode", OpenRouterJsonSchemaDefinition.string(),
                                        "addressCountry", OpenRouterJsonSchemaDefinition.string()
                                )
                        )
                )
        );
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_HISTORY_MESSAGE_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_HISTORY_MESSAGE_LENGTH);
    }

    private String trimmedOrNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean requiresRegion(String country) {
        String normalized = normalizedCountryCode(country);
        if (!hasText(normalized)) {
            return false;
        }
        return "US".equals(normalized) || "CA".equals(normalized);
    }

    private boolean isShippingRejection(CheckoutResult.Message message) {
        if (message == null) {
            return false;
        }
        String code = message.code() == null ? "" : message.code().trim().toLowerCase(Locale.ROOT);
        // Merchant content is localized, so the UCP message code is the reliable signal.
        if (code.contains("undeliverable") || code.contains("no_delivery_available")) {
            return true;
        }
        return isShippingRejection(message.content());
    }

    private boolean isShippingRejection(String message) {
        if (!hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        return normalized.contains("cannot be shipped")
                || normalized.contains("can't be shipped")
                || normalized.contains("does not ship")
                || normalized.contains("doesn't ship")
                || normalized.contains("no shipping")
                || normalized.contains("no delivery")
                || normalized.contains("delivery not available")
                || normalized.contains("shipping not available");
    }

    private record AssistantTurn(
            String reply,
            boolean readyToUpdate,
            AssistantBuyer buyer,
            AssistantAddress shippingAddress
    ) {
    }

    private record AssistantBuyer(
            String email,
            String firstName,
            String lastName,
            String phoneNumber
    ) {
    }

    private record AssistantAddress(
            String streetAddress,
            String extendedAddress,
            String addressLocality,
            String addressRegion,
            String postalCode,
            String addressCountry
    ) {
    }
}
