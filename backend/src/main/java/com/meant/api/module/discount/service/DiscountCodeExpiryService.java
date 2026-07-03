package com.meant.api.module.discount.service;

import com.meant.api.module.discount.constant.DiscountCodeStatus;
import com.meant.api.module.discount.properties.DiscountCodeSearchProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiscountCodeExpiryService {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/uuuu", Locale.US),
            DateTimeFormatter.ofPattern("MM/dd/uuuu", Locale.US),
            DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US),
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US),
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("d MMMM uuuu")
                    .toFormatter(Locale.US),
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("d MMM uuuu")
                    .toFormatter(Locale.US)
    );

    private final DiscountCodeSearchProperties properties;

    public Instant validUntil(String validUntilText, Instant now) {
        Instant parsed = parseValidUntil(validUntilText);
        return parsed != null && parsed.isAfter(now) ? parsed : null;
    }

    public Instant expiresAt(DiscountCodeStatus status, Instant validUntil, Instant now) {
        return switch (status) {
            case VALID -> validUntil == null ? now.plus(properties.candidateCacheTtl()) : validUntil;
            case INVALID -> now.plus(properties.invalidCacheTtl());
            case FAILED_RETRYABLE -> now.plus(properties.failedCacheTtl());
        };
    }

    private Instant parseValidUntil(String validUntilText) {
        if (validUntilText == null || validUntilText.isBlank()) {
            return null;
        }
        String value = cleanup(validUntilText);
        if (value == null) {
            return null;
        }
        Instant instant = parseInstant(value);
        if (instant != null) {
            return instant;
        }
        instant = parseDateTime(value);
        if (instant != null) {
            return instant;
        }
        return parseDate(value);
    }

    private String cleanup(String value) {
        String cleaned = value.trim()
                .replaceAll("(?i)^(expires?|valid\\s+(?:until|through|thru)|ends?)\\s*:?\\s*", "")
                .replaceAll("\\s+", " ");
        if (cleaned.isBlank() || cleaned.matches("(?i)^(unknown|none|n/a|not available|no expiry|no expiration)$")) {
            return null;
        }
        return cleaned;
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Instant parseDateTime(String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Instant parseDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(value, formatter);
                if (date.getYear() < 2000) {
                    return null;
                }
                return date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
