package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeList;
import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.service.command.UpdateCheckoutCommand;
import com.meant.api.plugin.checkout.common.dto.CheckoutBuyer;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.FulfillmentGroup;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.FulfillmentMethod;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.ShippingDestination;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Pure mapping and validation support for checkout fulfillment payloads.
 *
 * <p>Keeping this protocol-specific logic outside {@link CartService} makes the cart orchestration
 * flow easier to follow without adding another Spring bean to application startup.</p>
 */
final class CheckoutFulfillmentSupport {

    private CheckoutFulfillmentSupport() {
    }

    static CheckoutBuyer checkoutBuyer(UpdateCheckoutCommand.Buyer buyer) {
        return new CheckoutBuyer(
                trimToNull(buyer.firstName()),
                trimToNull(buyer.lastName()),
                trimToNull(buyer.email()),
                trimToNull(buyer.phoneNumber())
        );
    }

    static CheckoutFulfillment fulfillment(
            UpdateCheckoutCommand.Buyer buyer,
            UpdateCheckoutCommand.PostalAddress address,
            List<UpdateCheckoutRequest.LineItem> lineItems
    ) {
        ShippingDestination destination = shippingDestination(buyer, address);
        FulfillmentMethod method = new FulfillmentMethod(
                "shipping",
                "shipping",
                lineItemIds(lineItems),
                List.of(destination),
                "shipping",
                List.of()
        );
        return new CheckoutFulfillment(List.of(method));
    }

    static CheckoutFulfillment defaultFulfillmentSelection(
            UcpCheckoutResponse response,
            ShippingDestination destination
    ) {
        UcpCheckoutResponse.Checkout checkout = response == null ? null : response.resolvedCheckout();
        UcpCheckoutResponse.CheckoutFulfillment fulfillment = checkout == null ? null : checkout.fulfillment();
        if (fulfillment == null || fulfillment.methods().isEmpty()) {
            return null;
        }
        List<FulfillmentMethod> methods = fulfillment.methods().stream()
                .map(method -> defaultFulfillmentMethodSelection(method, destination))
                .filter(Objects::nonNull)
                .toList();
        return methods.isEmpty() ? null : new CheckoutFulfillment(methods);
    }

    static boolean hasCheckoutDetailsValidationProblems(UcpCheckoutResponse response) {
        if (response == null) {
            return false;
        }
        boolean hasValidationMessage = Stream.concat(
                        safeNonNullList(response.messages()).stream(),
                        response.resolvedCheckout() == null
                                ? Stream.empty()
                                : safeNonNullList(response.resolvedCheckout().messages()).stream()
                )
                .anyMatch(CheckoutFulfillmentSupport::isCheckoutDetailsValidationMessage);
        return hasValidationMessage || safeNonNullList(response.errors()).stream()
                .anyMatch(CheckoutFulfillmentSupport::isCheckoutDetailsValidationError);
    }

    static ShippingDestination shippingDestination(
            UpdateCheckoutCommand.Buyer buyer,
            UpdateCheckoutCommand.PostalAddress address
    ) {
        return new ShippingDestination(
                "shipping",
                trimToNull(address.extendedAddress()),
                trimToNull(address.streetAddress()),
                trimToNull(address.addressLocality()),
                trimToNull(address.addressRegion()),
                trimToNull(address.addressCountry()),
                trimToNull(address.postalCode()),
                trimToNull(buyer.firstName()),
                trimToNull(buyer.lastName()),
                trimToNull(buyer.phoneNumber())
        );
    }

    static boolean fulfillmentOptionsMissing(UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response == null ? null : response.resolvedCheckout();
        if (checkout == null) {
            return false;
        }
        UcpCheckoutResponse.CheckoutFulfillment fulfillment = checkout.fulfillment();
        if (fulfillment == null || fulfillment.methods().isEmpty()) {
            return true;
        }
        return fulfillment.methods().stream()
                .allMatch(method -> method == null || method.groups().isEmpty());
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static FulfillmentMethod defaultFulfillmentMethodSelection(
            UcpCheckoutResponse.CheckoutFulfillmentMethod method,
            ShippingDestination destination
    ) {
        if (method == null || method.groups().isEmpty()) {
            return null;
        }
        List<FulfillmentGroup> groups = method.groups().stream()
                .map(CheckoutFulfillmentSupport::defaultFulfillmentGroupSelection)
                .filter(Objects::nonNull)
                .toList();
        if (groups.isEmpty()) {
            return null;
        }
        // Update checkout uses replacement semantics, so the selection must carry the
        // destination again or the merchant may drop the shipping address.
        return new FulfillmentMethod(
                firstText(method.id(), method.type()),
                trimToNull(method.type()),
                normalizedStrings(method.lineItemIds()),
                destination == null ? List.of() : List.of(destination),
                selectedDestinationId(method),
                groups
        );
    }

    private static boolean isCheckoutDetailsValidationMessage(UcpCheckoutResponse.CheckoutMessage message) {
        if (message == null) {
            return false;
        }
        String code = normalizedValidationValue(message.code());
        if (isDeliveryCoverageProblem(code)) {
            return false;
        }
        if (isCheckoutDetailsValidationCode(code)) {
            return true;
        }
        boolean rejectsTargetedField = message.isError()
                || message.isRecoverable()
                || message.requiresBuyerAction()
                || hasValidationFailureName(code);
        return rejectsTargetedField
                && isCheckoutDetailsTarget(normalizedValidationValue(message.target()));
    }

    private static boolean isCheckoutDetailsValidationError(UcpCheckoutResponse.CheckoutError error) {
        if (error == null) {
            return false;
        }
        String code = normalizedValidationValue(error.code());
        if (isDeliveryCoverageProblem(code)) {
            return false;
        }
        return isCheckoutDetailsValidationCode(code)
                || isCheckoutDetailsValidationText(normalizedValidationValue(error.message()));
    }

    private static boolean isCheckoutDetailsValidationCode(String code) {
        if (code.startsWith("buyer_identity")) {
            return true;
        }
        return hasCheckoutDetailsFieldName(code) && hasValidationFailureName(code);
    }

    private static boolean isCheckoutDetailsValidationText(String message) {
        return hasCheckoutDetailsFieldName(message) && hasValidationFailureName(message);
    }

    private static boolean hasCheckoutDetailsFieldName(String value) {
        return value.contains("buyer")
                || value.contains("email")
                || value.contains("phone")
                || value.contains("first_name")
                || value.contains("last_name")
                || value.contains("address")
                || value.contains("postal")
                || value.contains("zip")
                || value.contains("country")
                || value.contains("region")
                || value.contains("state")
                || value.contains("locality")
                || value.contains("city")
                || value.contains("street");
    }

    private static boolean hasValidationFailureName(String value) {
        return value.contains("invalid")
                || value.contains("validation")
                || value.contains("required")
                || value.contains("missing")
                || value.contains("incomplete")
                || value.contains("malformed")
                || value.contains("rejected");
    }

    private static boolean isCheckoutDetailsTarget(String target) {
        return target.startsWith("$.buyer")
                || target.contains("shipping_address")
                || target.contains("delivery_address")
                || target.contains("shippingaddress")
                || target.contains("deliveryaddress")
                || target.contains("destination");
    }

    private static boolean isDeliveryCoverageProblem(String code) {
        return code.contains("undeliverable")
                || code.contains("no_delivery_available")
                || code.contains("delivery_not_available")
                || code.contains("shipping_not_available")
                || code.contains("unsupported_destination")
                || code.contains("outside_delivery_area");
    }

    private static String normalizedValidationValue(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static FulfillmentGroup defaultFulfillmentGroupSelection(
            UcpCheckoutResponse.CheckoutFulfillmentGroup group
    ) {
        if (group == null || hasText(group.selectedOptionId())) {
            return null;
        }
        String selectedOptionId = defaultOptionId(group);
        if (!hasText(group.id()) || !hasText(selectedOptionId)) {
            return null;
        }
        return new FulfillmentGroup(
                group.id().trim(),
                normalizedStrings(group.lineItemIds()),
                List.of(),
                selectedOptionId.trim()
        );
    }

    private static String selectedDestinationId(UcpCheckoutResponse.CheckoutFulfillmentMethod method) {
        if (hasText(method.selectedDestinationId())) {
            return method.selectedDestinationId().trim();
        }
        return method.destinations().stream()
                .map(UcpCheckoutResponse.CheckoutAddress::id)
                .filter(CheckoutFulfillmentSupport::hasText)
                .findFirst()
                .orElse(null);
    }

    private static String defaultOptionId(UcpCheckoutResponse.CheckoutFulfillmentGroup group) {
        String selected = group.selectedOption();
        if (hasText(selected)) {
            return selected.trim();
        }
        return group.options().stream()
                .map(UcpCheckoutResponse.CheckoutFulfillmentOption::resolvedId)
                .filter(CheckoutFulfillmentSupport::hasText)
                .findFirst()
                .orElse(null);
    }

    private static List<String> lineItemIds(List<UpdateCheckoutRequest.LineItem> lineItems) {
        return safeList(lineItems).stream()
                .map(UpdateCheckoutRequest.LineItem::id)
                .filter(CheckoutFulfillmentSupport::hasText)
                .map(String::trim)
                .toList();
    }

    private static List<String> normalizedStrings(List<String> values) {
        return safeList(values).stream()
                .filter(CheckoutFulfillmentSupport::hasText)
                .map(String::trim)
                .toList();
    }
}
