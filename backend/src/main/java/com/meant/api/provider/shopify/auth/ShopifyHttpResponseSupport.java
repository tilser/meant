package com.meant.api.provider.shopify.auth;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

final class ShopifyHttpResponseSupport {

    private ShopifyHttpResponseSupport() {
    }

    static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    static Duration retryAfter(HttpHeaders headers, Clock clock) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration duration = Duration.between(clock.instant(), retryAt);
                return duration.isNegative() ? Duration.ZERO : duration;
            } catch (DateTimeException | ArithmeticException invalidDate) {
                return null;
            }
        }
    }
}
