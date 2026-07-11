package com.meant.api.module.cart.service;

import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
                && mapSubset(intended.context(), checkout.context())
                && discounts(intended.discountCodes(), checkout.discounts())
                && fulfillment(intended.fulfillment(), checkout.fulfillment());
    }

    private boolean lines(
            List<UpdateCheckoutRequest.LineItem> expected,
            List<UcpCheckoutResponse.CheckoutLineItem> actual
    ) {
        List<ObservableLine> remaining = new ArrayList<>(actual.stream().filter(Objects::nonNull)
                .map(line -> new ObservableLine(
                        text(line.resolvedId()), text(line.resolvedVariantId()), line.quantity()))
                .toList());
        if (expected.size() != remaining.size()) {
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

    private boolean buyer(Map<String, Object> expected, UcpCheckoutResponse.CheckoutBuyer actual) {
        if (expected.isEmpty()) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        Map<String, Object> observed = new HashMap<>();
        observed.put("first_name", actual.firstName());
        observed.put("last_name", actual.lastName());
        observed.put("email", actual.email());
        observed.put("phone_number", actual.phoneNumber());
        return aliases(expected, observed);
    }

    private boolean aliases(Map<String, Object> expected, Map<String, Object> observed) {
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            String key = switch (entry.getKey()) {
                case "firstName" -> "first_name";
                case "lastName" -> "last_name";
                case "phone", "phoneNumber" -> "phone_number";
                default -> entry.getKey();
            };
            if (!Objects.equals(entry.getValue(), observed.get(key))) {
                return false;
            }
        }
        return true;
    }

    private boolean value(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }

    private boolean mapSubset(Map<String, Object> expected, Map<String, Object> actual) {
        if (expected.isEmpty()) {
            return true;
        }
        return actual != null && expected.entrySet().stream()
                .allMatch(entry -> Objects.equals(entry.getValue(), actual.get(entry.getKey())));
    }

    private boolean discounts(List<String> expected, UcpCheckoutResponse.CheckoutDiscounts actual) {
        if (expected.isEmpty()) {
            return true;
        }
        return actual != null && Set.copyOf(actual.codes()).equals(Set.copyOf(expected));
    }

    private boolean fulfillment(Map<String, Object> expected, UcpCheckoutResponse.CheckoutFulfillment actual) {
        if (expected.isEmpty()) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        List<Map<String, Object>> methods = maps(expected.get("methods"));
        if (methods.isEmpty()) {
            return false;
        }
        Map<String, Object> method = methods.getFirst();
        List<Map<String, Object>> destinations = maps(method.get("destinations"));
        if (!destinations.isEmpty() && !address(destinations.getFirst(), actual.resolvedShippingAddress())) {
            return false;
        }
        Set<String> selected = maps(method.get("groups")).stream()
                .map(group -> text(group.get("selected_option_id"))).filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
        if (!selected.isEmpty()) {
            Set<String> observed = actual.methods().stream().filter(Objects::nonNull)
                    .flatMap(value -> value.groups().stream()).filter(Objects::nonNull)
                    .map(UcpCheckoutResponse.CheckoutFulfillmentGroup::selectedOption)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            return observed.containsAll(selected);
        }
        return true;
    }

    private boolean address(Map<String, Object> expected, UcpCheckoutResponse.CheckoutAddress actual) {
        if (actual == null) {
            return false;
        }
        Map<String, Object> observed = Map.of(
                "id", text(actual.id()),
                "street_address", text(actual.streetAddress()),
                "address_locality", text(actual.addressLocality()),
                "address_region", text(actual.addressRegion()),
                "postal_code", text(actual.postalCode()),
                "address_country", text(actual.addressCountry()),
                "first_name", text(actual.firstName()),
                "last_name", text(actual.lastName()),
                "phone_number", text(actual.phoneNumber()));
        return expected.entrySet().stream()
                .allMatch(entry -> observed.containsKey(entry.getKey())
                        && Objects.equals(text(entry.getValue()), observed.get(entry.getKey())));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> values = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new HashMap<>();
                map.forEach((key, entry) -> copy.put(String.valueOf(key), entry));
                values.add(copy);
            }
        }
        return values;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record ObservableLine(String id, String variant, Integer quantity) {
    }
}
