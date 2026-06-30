package com.meant.api.plugin.payment.common.support;

import com.meant.api.plugin.payment.common.dto.PaymentBinding;
import com.meant.api.plugin.payment.common.exception.PaymentHandlerException;
import com.meant.api.plugin.support.UcpDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class PaymentBindingValidator {

    private PaymentBindingValidator() {
    }

    public static void requireBinding(PaymentBinding expected, PaymentBinding actual, String context) {
        Objects.requireNonNull(expected, "expected must not be null");
        Objects.requireNonNull(actual, "actual must not be null");
        requireEqual(expected.checkoutId(), actual.checkoutId(), context, "checkout id");
        requireEqual(expected.merchantId(), actual.merchantId(), context, "merchant id");
        requireEqual(expected.merchantDomain(), actual.merchantDomain(), context, "merchant domain");
        requireEqual(expected.pspMerchantId(), actual.pspMerchantId(), context, "PSP merchant id");
        requireEqual(expected.pspMerchantDomain(), actual.pspMerchantDomain(), context, "PSP merchant domain");
        requireEqual(expected.amountMinor(), actual.amountMinor(), context, "amount");
        requireEqual(expected.currency(), actual.currency(), context, "currency");
    }

    public static void requireFreshCryptogram(
            Instant issuedAt,
            Instant expiresAt,
            Instant validatedAt,
            Duration maximumAge,
            String context
    ) {
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(validatedAt, "validatedAt must not be null");
        Objects.requireNonNull(maximumAge, "maximumAge must not be null");
        if (issuedAt.isAfter(validatedAt)) {
            throw new PaymentHandlerException(context + " cryptogram is from the future");
        }
        if (!expiresAt.isAfter(validatedAt)) {
            throw new PaymentHandlerException(context + " cryptogram is stale");
        }
        if (issuedAt.plus(maximumAge).isBefore(validatedAt)) {
            throw new PaymentHandlerException(context + " cryptogram is older than the allowed freshness window");
        }
    }

    public static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new PaymentHandlerException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    public static Long requireAmount(Long amount, String fieldName) {
        Objects.requireNonNull(amount, fieldName + " must not be null");
        if (amount < 0) {
            throw new PaymentHandlerException(fieldName + " must not be negative");
        }
        return amount;
    }

    public static String normalizedCurrency(String currency, String fieldName) {
        String normalized = UcpDecimal.normalizedCurrency(currency);
        if (normalized == null) {
            throw new PaymentHandlerException(fieldName + " must not be blank");
        }
        return normalized;
    }

    public static String normalizedDomain(String domain, String fieldName) {
        String trimmed = requireText(domain, fieldName);
        String candidate = trimmed.contains("://") ? trimmed : "https://" + trimmed;
        try {
            URI uri = URI.create(candidate);
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host.trim().toLowerCase(java.util.Locale.ROOT);
            }
        } catch (IllegalArgumentException ignored) {
        }
        return trimmed.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static void requireEqual(Object expected, Object actual, String context, String fieldName) {
        if (!Objects.equals(expected, actual)) {
            throw new PaymentHandlerException(context + " " + fieldName + " binding mismatch");
        }
    }
}
