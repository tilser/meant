package com.meant.api.plugin.checkout.common.service;

import com.meant.api.plugin.checkout.common.exception.UcpCheckoutSafetyException;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentShippingAddress;
import com.meant.api.plugin.signing.Jcs;
import com.meant.api.plugin.support.UcpDecimal;
import com.meant.api.plugin.support.UcpMoney;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
public class CheckoutTotalsReconciler {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Jcs jcs;

    @Autowired
    public CheckoutTotalsReconciler(ObjectMapper objectMapper, Jcs jcs) {
        this.objectMapper = objectMapper;
        this.jcs = jcs;
    }

    public ReconciliationResult reconcile(@NotNull @Valid ExpectedCheckout expected, @NotNull Object checkout) {
        ObservedCheckout observed = observedCheckout(checkout);
        List<String> violations = new ArrayList<>();

        requireEqual("checkout id", expected.checkoutId(), observed.checkoutId(), violations);
        requireEqual("merchant identity", expected.merchantId(), observed.merchantId(), violations);
        String expectedCurrency = normalizedCurrency(expected.currency());
        String actualCurrency = normalizedCurrency(observed.currency());
        requireEqual("currency", expectedCurrency, actualCurrency, violations);
        requireEqual("total amount", expected.totalAmountMinor(), observed.totalAmountMinor(), violations);
        requireOptionalEqual("tax amount", expected.taxAmountMinor(), observed.taxAmountMinor(), violations);
        requireOptionalEqual("discount amount", expected.discountAmountMinor(), observed.discountAmountMinor(), violations);
        requireOptionalEqual("tip amount", expected.tipAmountMinor(), observed.tipAmountMinor(), violations);
        requireOptionalEqual("shipping method", expected.shippingMethod(), observed.shippingMethod(), violations);
        requireOptionalCanonicalEqual(
                "shipping address",
                normalizedShippingAddress(expected.shippingAddress()),
                observed.shippingAddress(),
                violations
        );
        requireOptionalCanonicalEqual("subscription terms", expected.subscriptionTerms(), observed.subscriptionTerms(), violations);
        reconcileLineItems(expected.lineItems(), observed.lineItems(), violations);
        reconcileSpendCeiling(expected, observed, violations);

        return new ReconciliationResult(violations.isEmpty(), List.copyOf(violations), observed);
    }

    public void rejectIfMismatch(@NotNull @Valid ExpectedCheckout expected, @NotNull Object checkout) {
        ReconciliationResult result = reconcile(expected, checkout);
        if (!result.match()) {
            throw new UcpCheckoutSafetyException("Checkout totals reconciliation failed: "
                    + String.join("; ", result.violations()));
        }
    }

    private void reconcileSpendCeiling(
            ExpectedCheckout expected,
            ObservedCheckout observed,
            List<String> violations
    ) {
        if (expected.maxAuthorizedAmountMinor() == null || observed.totalAmountMinor() == null) {
            return;
        }
        if (observed.totalAmountMinor() > expected.maxAuthorizedAmountMinor()) {
            violations.add("total amount exceeds authorized spend ceiling");
        }
    }

    private void reconcileLineItems(
            List<ExpectedLineItem> expectedItems,
            List<ObservedLineItem> observedItems,
            List<String> violations
    ) {
        List<ExpectedLineItem> expected = safeList(expectedItems).stream()
                .sorted(Comparator.comparing(this::lineKey))
                .toList();
        List<ObservedLineItem> observed = safeList(observedItems).stream()
                .sorted(Comparator.comparing(this::lineKey))
                .toList();
        if (expected.size() != observed.size()) {
            violations.add("line item count mismatch");
            return;
        }
        for (int index = 0; index < expected.size(); index++) {
            ExpectedLineItem expectedLine = expected.get(index);
            ObservedLineItem observedLine = observed.get(index);
            String lineContext = "line item " + lineKey(expectedLine);
            requireEqual(lineContext + " key", lineKey(expectedLine), lineKey(observedLine), violations);
            requireEqual(lineContext + " quantity", expectedLine.quantity(), observedLine.quantity(), violations);
            requireEqual(lineContext + " total amount", expectedLine.totalAmountMinor(), observedLine.totalAmountMinor(), violations);
            requireEqual(
                    lineContext + " currency",
                    normalizedCurrency(expectedLine.currency()),
                    normalizedCurrency(observedLine.currency()),
                    violations
            );
        }
    }

    private ObservedCheckout observedCheckout(Object checkout) {
        Map<String, Object> root = objectMap(checkout);
        Map<String, Object> checkoutMap = mapValue(root, "checkout");
        if (checkoutMap == null) {
            checkoutMap = root;
        }

        String currency = firstScalar(checkoutMap, "currency", "currencyCode");
        UcpMoney total = money(checkoutMap, currency, "total_amount", "totalAmount", "total", "amount", "grand_total");
        if (total != null && total.currency() != null) {
            currency = total.currency();
        }

        return new ObservedCheckout(
                firstScalar(checkoutMap, "id", "checkout_id", "checkoutId"),
                observedMerchantId(root, checkoutMap),
                total == null ? null : total.amount(),
                currency,
                moneyAmount(checkoutMap, currency, "tax_amount", "taxAmount", "totalTaxAmount", "tax"),
                moneyAmount(checkoutMap, currency, "discount_amount", "discountAmount", "totalDiscountAmount", "discount"),
                moneyAmount(checkoutMap, currency, "tip_amount", "tipAmount", "tip"),
                shippingAddress(checkoutMap),
                shippingMethod(checkoutMap),
                subscriptionTerms(checkoutMap),
                observedLineItems(checkoutMap, currency)
        );
    }

    private String observedMerchantId(Map<String, Object> root, Map<String, Object> checkout) {
        String merchantId = firstScalar(checkout, "merchant_id", "merchantId");
        if (merchantId != null) {
            return merchantId;
        }
        Map<String, Object> merchant = mapValue(checkout, "merchant");
        if (merchant == null) {
            merchant = mapValue(root, "merchant");
        }
        return merchant == null ? null : firstScalar(merchant, "id", "merchant_id", "merchantId", "domain");
    }

    private List<ObservedLineItem> observedLineItems(Map<String, Object> checkout, String fallbackCurrency) {
        List<?> lines = listValue(checkout, "line_items", "lineItems", "lines", "items");
        if (lines == null) {
            return List.of();
        }
        List<ObservedLineItem> observed = new ArrayList<>();
        for (Object line : lines) {
            Map<String, Object> lineMap = objectMap(line);
            String currency = firstScalar(lineMap, "currency", "currencyCode");
            if (currency == null) {
                currency = fallbackCurrency;
            }
            UcpMoney total = money(lineMap, currency, "total_amount", "totalAmount", "total", "amount");
            Map<String, Object> merchandise = mapValue(lineMap, "merchandise");
            observed.add(new ObservedLineItem(
                    firstScalar(lineMap, "id", "line_id", "lineId"),
                    firstPresent(
                            firstScalar(lineMap, "product_variant_id", "productVariantId", "variant_id", "variantId"),
                            merchandise == null ? null : firstScalar(merchandise, "id", "product_variant_id", "variant_id")
                    ),
                    integerValue(firstValue(lineMap, "quantity", "qty")),
                    total == null ? null : total.amount(),
                    total == null ? currency : firstPresent(total.currency(), currency)
            ));
        }
        return observed;
    }

    private Object shippingAddress(Map<String, Object> checkout) {
        Object address = firstValue(checkout, "shipping_address", "shippingAddress", "deliveryAddress");
        if (address != null) {
            return normalizedShippingAddress(address);
        }
        Map<String, Object> fulfillment = mapValue(checkout, "fulfillment");
        return fulfillment == null
                ? null
                : normalizedShippingAddress(firstValue(fulfillment, "shipping_address", "shippingAddress", "address"));
    }

    private BuyerConsentShippingAddress normalizedShippingAddress(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BuyerConsentShippingAddress address) {
            return address.isEmpty() ? null : address;
        }
        Map<String, Object> address = objectMap(value);
        if (address == null || address.isEmpty()) {
            return null;
        }
        BuyerConsentShippingAddress normalized = new BuyerConsentShippingAddress(
                firstScalar(address, "street_address", "streetAddress", "address1"),
                firstScalar(address, "address_locality", "addressLocality", "city"),
                firstScalar(address, "address_region", "addressRegion", "province", "provinceCode"),
                firstScalar(address, "postal_code", "postalCode", "zip"),
                firstScalar(address, "address_country", "addressCountry", "country", "countryCode")
        );
        return normalized.isEmpty() ? null : normalized;
    }

    private String shippingMethod(Map<String, Object> checkout) {
        Object method = firstValue(checkout, "shipping_method", "shippingMethod", "selectedShippingMethod", "deliveryMethod");
        if (method == null) {
            Map<String, Object> fulfillment = mapValue(checkout, "fulfillment");
            method = fulfillment == null ? null : firstValue(fulfillment, "shipping_method", "shippingMethod", "method");
        }
        if (method instanceof Map<?, ?> map) {
            Map<String, Object> methodMap = stringKeyMap(map);
            return firstScalar(methodMap, "id", "handle", "name", "title");
        }
        return scalarString(method);
    }

    private Object subscriptionTerms(Map<String, Object> checkout) {
        return firstValue(checkout, "subscription", "recurring", "recurring_terms", "recurringTerms", "trial_terms", "trialTerms");
    }

    private UcpMoney money(Map<String, Object> source, String fallbackCurrency, String... keys) {
        Object value = firstValue(source, keys);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> moneyMap = stringKeyMap(map);
            Object nested = firstValue(moneyMap, "total_amount", "totalAmount", "amount", "value", "price");
            if (nested != null && !moneyMap.containsKey("amount") && !moneyMap.containsKey("value")) {
                value = nested;
            }
        }
        return UcpMoney.value(value, fallbackCurrency);
    }

    private Long moneyAmount(Map<String, Object> source, String fallbackCurrency, String... keys) {
        UcpMoney money = money(source, fallbackCurrency, keys);
        return money == null ? null : money.amount();
    }

    private Map<String, Object> objectMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            return stringKeyMap(map);
        }
        try {
            if (value instanceof String string) {
                Map<String, Object> parsed = objectMapper.readValue(string, MAP_TYPE);
                return parsed == null ? Map.of() : parsed;
            }
            Map<String, Object> converted = objectMapper.convertValue(value, MAP_TYPE);
            return converted == null ? Map.of() : converted;
        } catch (IllegalArgumentException | JacksonException exception) {
            throw new UcpCheckoutSafetyException("Checkout payload could not be read for reconciliation", exception);
        }
    }

    private Map<String, Object> mapValue(Map<String, Object> source, String key) {
        Object value = firstValue(source, key);
        return value instanceof Map<?, ?> map ? stringKeyMap(map) : null;
    }

    private List<?> listValue(Map<String, Object> source, String... keys) {
        Object value = firstValue(source, keys);
        return value instanceof List<?> list ? list : null;
    }

    private Object firstValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private String firstScalar(Map<String, Object> source, String... keys) {
        return scalarString(firstValue(source, keys));
    }

    private String scalarString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string.isBlank() ? null : string.trim();
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return value.toString();
        }
        return null;
    }

    private Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String scalar = scalarString(value);
        if (scalar == null || !scalar.matches("-?\\d+")) {
            return null;
        }
        try {
            return Integer.parseInt(scalar);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> values = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) {
                values.put(key.toString(), value);
            }
        });
        return values;
    }

    private void requireEqual(String field, Object expected, Object actual, List<String> violations) {
        if (!Objects.equals(expected, actual)) {
            violations.add(field + " mismatch");
        }
    }

    private void requireOptionalEqual(String field, Object expected, Object actual, List<String> violations) {
        if (expected != null && !Objects.equals(expected, actual)) {
            violations.add(field + " mismatch");
        }
    }

    private void requireOptionalCanonicalEqual(String field, Object expected, Object actual, List<String> violations) {
        if (expected == null) {
            return;
        }
        if (!Objects.equals(canonical(expected), canonical(actual))) {
            violations.add(field + " mismatch");
        }
    }

    private String canonical(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new String(jcs.canonicalizeToUtf8Bytes(objectMapper.writeValueAsBytes(value)), StandardCharsets.UTF_8);
        } catch (JacksonException exception) {
            throw new UcpCheckoutSafetyException("Checkout value could not be canonicalized", exception);
        }
    }

    private String normalizedCurrency(String currency) {
        String normalized = UcpDecimal.normalizedCurrency(currency);
        return normalized == null ? "" : normalized;
    }

    private String firstPresent(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String lineKey(ExpectedLineItem line) {
        return Objects.requireNonNullElse(firstPresent(line.productVariantId(), line.id()), "");
    }

    private String lineKey(ObservedLineItem line) {
        return Objects.requireNonNullElse(firstPresent(line.productVariantId(), line.id()), "");
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record ExpectedCheckout(
            @NotBlank String checkoutId,
            @NotBlank String merchantId,
            @NotNull Long totalAmountMinor,
            @NotBlank String currency,
            List<@Valid ExpectedLineItem> lineItems,
            Long taxAmountMinor,
            Long discountAmountMinor,
            Long tipAmountMinor,
            Object shippingAddress,
            String shippingMethod,
            Object subscriptionTerms,
            Long maxAuthorizedAmountMinor
    ) {
    }

    public record ExpectedLineItem(
            String id,
            String productVariantId,
            @NotNull Integer quantity,
            @NotNull Long totalAmountMinor,
            @NotBlank String currency
    ) {
    }

    public record ObservedCheckout(
            String checkoutId,
            String merchantId,
            Long totalAmountMinor,
            String currency,
            Long taxAmountMinor,
            Long discountAmountMinor,
            Long tipAmountMinor,
            Object shippingAddress,
            String shippingMethod,
            Object subscriptionTerms,
            List<ObservedLineItem> lineItems
    ) {
    }

    public record ObservedLineItem(
            String id,
            String productVariantId,
            Integer quantity,
            Long totalAmountMinor,
            String currency
    ) {
    }

    public record ReconciliationResult(
            boolean match,
            List<String> violations,
            ObservedCheckout observedCheckout
    ) {
    }
}
