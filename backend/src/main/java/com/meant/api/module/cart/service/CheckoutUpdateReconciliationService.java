package com.meant.api.module.cart.service;

import com.meant.api.plugin.checkout.common.dto.CheckoutBuyer;
import com.meant.api.plugin.checkout.common.dto.CheckoutContext;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.FulfillmentDestination;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.FulfillmentGroup;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.FulfillmentMethod;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.RetailLocation;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.ShippingDestination;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Proves only fields that Checkout MCP returned; absent evidence never becomes success. */
@Service
public class CheckoutUpdateReconciliationService {

    public boolean proves(UpdateCheckoutRequest intended, UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response == null ? null : response.resolvedCheckout();
        return checkout != null
                && lines(intended.lineItems(), checkout.lineItems())
                && buyer(intended.buyer(), checkout.buyer())
                && value(intended.email(), checkout.buyer() == null ? null : checkout.buyer().email())
                && value(intended.currency(), checkout.currency())
                && context(intended.context(), checkout.context())
                && discounts(intended.discountCodes(), checkout.discounts())
                && fulfillment(intended.fulfillment(), checkout.fulfillment());
    }

    private boolean lines(
            List<UpdateCheckoutRequest.LineItem> expected,
            List<UcpCheckoutResponse.CheckoutLineItem> actual
    ) {
        List<ObservableLine> remaining = new ArrayList<>(safe(actual).stream()
                .filter(Objects::nonNull)
                .map(line -> new ObservableLine(
                        text(line.resolvedId()), text(line.resolvedVariantId()), line.quantity()))
                .toList());
        if (safe(expected).size() != remaining.size()) {
            return false;
        }
        for (UpdateCheckoutRequest.LineItem line : expected) {
            ObservableLine intended = new ObservableLine(
                    text(line.id()), text(line.productVariantId()), line.quantity());
            int match = -1;
            for (int index = 0; index < remaining.size(); index++) {
                if (sameObservableLine(intended, remaining.get(index))) {
                    match = index;
                    break;
                }
            }
            if (match < 0) {
                return false;
            }
            remaining.remove(match);
        }
        return true;
    }

    private boolean sameObservableLine(ObservableLine expected, ObservableLine actual) {
        boolean idMatches = expected.id().isEmpty() || expected.id().equals(actual.id());
        return idMatches && !expected.variant().isEmpty()
                && expected.variant().equals(actual.variant())
                && Objects.equals(expected.quantity(), actual.quantity());
    }

    private boolean buyer(CheckoutBuyer expected, UcpCheckoutResponse.CheckoutBuyer actual) {
        if (expected == null || expected.empty()) {
            return true;
        }
        return actual != null
                && value(expected.firstName(), actual.firstName())
                && value(expected.lastName(), actual.lastName())
                && value(expected.email(), actual.email())
                && value(expected.phoneNumber(), actual.phoneNumber());
    }

    private boolean context(CheckoutContext expected, CheckoutContext actual) {
        if (expected == null || expected.empty()) {
            return true;
        }
        if (actual == null
                || !value(expected.addressCountry(), actual.addressCountry())
                || !value(expected.addressRegion(), actual.addressRegion())
                || !value(expected.postalCode(), actual.postalCode())
                || !value(expected.intent(), actual.intent())
                || !value(expected.language(), actual.language())
                || !value(expected.currency(), actual.currency())) {
            return false;
        }
        if (!expected.eligibility().isEmpty() && !expected.eligibility().equals(actual.eligibility())) {
            return false;
        }
        return expected.extensionValues().entrySet().stream()
                .allMatch(entry -> Objects.equals(entry.getValue(), actual.extensionValues().get(entry.getKey())));
    }

    private boolean value(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }

    private boolean discounts(List<String> expected, UcpCheckoutResponse.CheckoutDiscounts actual) {
        if (expected == null) {
            return true;
        }
        List<String> actualCodes = actual == null ? List.of() : safe(actual.codes());
        return Set.copyOf(actualCodes).equals(Set.copyOf(expected));
    }

    private boolean fulfillment(CheckoutFulfillment expected, UcpCheckoutResponse.CheckoutFulfillment actual) {
        if (expected == null || expected.empty()) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        return expected.methods().stream().allMatch(expectedMethod -> actual.methods().stream()
                .filter(Objects::nonNull)
                .anyMatch(actualMethod -> fulfillmentMethod(expectedMethod, actualMethod)));
    }

    private boolean fulfillmentMethod(
            FulfillmentMethod expected,
            UcpCheckoutResponse.CheckoutFulfillmentMethod actual
    ) {
        if (expected == null
                || !value(expected.id(), actual.id())
                || !value(expected.type(), actual.type())) {
            return expected == null;
        }
        if (!expected.lineItemIds().isEmpty()
                && !Set.copyOf(expected.lineItemIds()).equals(Set.copyOf(actual.lineItemIds()))) {
            return false;
        }
        if (!value(expected.selectedDestinationId(), actual.selectedDestinationId())) {
            return false;
        }
        if (!expected.destinations().stream().allMatch(destination -> destination(destination, actual.destinations()))) {
            return false;
        }
        return expected.groups().stream().allMatch(expectedGroup -> group(expectedGroup, actual.groups()));
    }

    private boolean destination(
            FulfillmentDestination expected,
            List<UcpCheckoutResponse.CheckoutAddress> actual
    ) {
        if (expected instanceof ShippingDestination shipping) {
            return safe(actual).stream()
                    .filter(Objects::nonNull)
                    .anyMatch(address -> shippingAddress(shipping, address));
        }
        if (expected instanceof RetailLocation retailLocation) {
            return safe(actual).stream()
                    .filter(Objects::nonNull)
                    .anyMatch(address -> value(retailLocation.id(), address.id()));
        }
        return false;
    }

    private boolean shippingAddress(
            ShippingDestination expected,
            UcpCheckoutResponse.CheckoutAddress actual
    ) {
        return value(expected.id(), actual.id())
                && value(expected.streetAddress(), actual.streetAddress())
                && value(expected.addressLocality(), actual.addressLocality())
                && value(expected.addressRegion(), actual.addressRegion())
                && value(expected.postalCode(), actual.postalCode())
                && value(expected.addressCountry(), actual.addressCountry())
                && value(expected.firstName(), actual.firstName())
                && value(expected.lastName(), actual.lastName())
                && value(expected.phoneNumber(), actual.phoneNumber());
    }

    private boolean group(
            FulfillmentGroup expected,
            List<UcpCheckoutResponse.CheckoutFulfillmentGroup> actual
    ) {
        return safe(actual).stream()
                .filter(Objects::nonNull)
                .filter(candidate -> value(expected.id(), candidate.id()))
                .anyMatch(candidate -> value(expected.selectedOptionId(), candidate.selectedOption()));
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private record ObservableLine(String id, String variant, Integer quantity) {
    }
}
