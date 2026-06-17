package com.meant.api.module.user.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.CatalogSearchFilters;
import com.meant.api.module.merchant.service.dto.CatalogSearchPriceFilter;
import com.meant.api.module.merchant.service.dto.CatalogSearchSignals;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserProductSearchCatalogInput;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class UserProductSearchCatalogInputBuilder {

    private static final String AMOUNT_NUMBER_PATTERN =
            "(?:\\d{1,3}(?:[\\.,]\\d{3})+|\\d+)(?:[\\.,]\\d{1,2})?";
    private static final String AMOUNT_PATTERN = "(?:[$€£¥]\\s*)?" + AMOUNT_NUMBER_PATTERN;
    private static final String CURRENCY_PATTERN =
            "(?:usd|u\\.s\\. dollars?|us dollars?|dollars?|eur|euros?|gbp|pounds?|czk|crowns?|cad|canadian dollars?|aud|australian dollars?|jpy|yen)";
    private static final Pattern BETWEEN_PRICE_PATTERN = Pattern.compile(
            "\\b(?:between|from)\\s+(?<min>%s)\\s+(?:and|to|-)\\s+(?<max>%s)\\s*(?<currency>%s)?\\b"
                    .formatted(AMOUNT_PATTERN, AMOUNT_PATTERN, CURRENCY_PATTERN),
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MAX_PRICE_PATTERN = Pattern.compile(
            "\\b(?:under|below|less than|up to|max(?:imum)?|no more than)\\s+(?<max>%s)\\s*(?<currency>%s)?\\b"
                    .formatted(AMOUNT_PATTERN, CURRENCY_PATTERN),
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MAX_PRICE_WITH_PREFIXED_CURRENCY_PATTERN = Pattern.compile(
            "\\b(?:under|below|less than|up to|max(?:imum)?|no more than)\\s+(?<currency>%s)\\s+(?<max>%s)\\b"
                    .formatted(CURRENCY_PATTERN, AMOUNT_NUMBER_PATTERN),
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MIN_PRICE_PATTERN = Pattern.compile(
            "\\b(?:over|above|more than|at least|min(?:imum)?|no less than)\\s+(?<min>%s)\\s*(?<currency>%s)?\\b"
                    .formatted(AMOUNT_PATTERN, CURRENCY_PATTERN),
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MIN_PRICE_WITH_PREFIXED_CURRENCY_PATTERN = Pattern.compile(
            "\\b(?:over|above|more than|at least|min(?:imum)?|no less than)\\s+(?<currency>%s)\\s+(?<min>%s)\\b"
                    .formatted(CURRENCY_PATTERN, AMOUNT_NUMBER_PATTERN),
            Pattern.CASE_INSENSITIVE
    );
    private static final List<Pattern> PRICE_PATTERNS = List.of(
            BETWEEN_PRICE_PATTERN,
            MAX_PRICE_PATTERN,
            MAX_PRICE_WITH_PREFIXED_CURRENCY_PATTERN,
            MIN_PRICE_PATTERN,
            MIN_PRICE_WITH_PREFIXED_CURRENCY_PATTERN
    );
    private static final int MAX_INTENT_LENGTH = 1200;

    private final UserProductSearchHashService userProductSearchHashService;

    public UserProductSearchCatalogInputBuilder(UserProductSearchHashService userProductSearchHashService) {
        this.userProductSearchHashService = userProductSearchHashService;
    }

    public UserProductSearchCatalogInput build(
            String originalQuery,
            UserProductSearchQueryIntentResult queryIntent,
            UserSettingsResult settings
    ) {
        return build(originalQuery, queryIntent, settings, null, null);
    }

    public UserProductSearchCatalogInput build(
            String originalQuery,
            UserProductSearchQueryIntentResult queryIntent,
            UserSettingsResult settings,
            String buyerIp,
            String userAgent
    ) {
        ParsedPrice parsedPrice = parsePrice(originalQuery, queryIntent.searchQuery());
        ParsedPrice priceFilter = priceFilter(parsedPrice, settings);
        String searchQuery = searchQuery(queryIntent.searchQuery(), parsedPrice);
        String country = countryCode(settings.location());
        String currency = firstPresent(
                parsedPrice == null ? null : parsedPrice.currency(),
                defaultCurrency(country),
                priceFilter == null ? null : "USD"
        );
        CatalogSearchContext context = context(
                country,
                currency,
                intent(originalQuery, searchQuery, parsedPrice, priceFilter, currency, queryIntent, settings)
        );
        CatalogSearchSignals signals = signals(buyerIp, userAgent);
        CatalogSearchFilters filters = filters(priceFilter, currency);
        return new UserProductSearchCatalogInput(
                searchQuery,
                cacheKey(queryIntent, searchQuery, context, filters),
                context,
                signals,
                filters
        );
    }

    private CatalogSearchSignals signals(String buyerIp, String userAgent) {
        String sanitizedBuyerIp = sanitizedSignal(buyerIp, 128);
        String sanitizedUserAgent = sanitizedSignal(userAgent, 512);
        if (sanitizedBuyerIp == null && sanitizedUserAgent == null) {
            return null;
        }
        return new CatalogSearchSignals(sanitizedBuyerIp, sanitizedUserAgent);
    }

    private ParsedPrice priceFilter(ParsedPrice parsedPrice, UserSettingsResult settings) {
        if (parsedPrice != null) {
            return parsedPrice;
        }
        if (settings.budget() == null) {
            return null;
        }
        return new ParsedPrice(null, BigDecimal.valueOf(settings.budget()), null);
    }

    private String searchQuery(String queryIntentSearchQuery, ParsedPrice parsedPrice) {
        String searchQuery = parsedPrice == null
                ? queryIntentSearchQuery
                : stripPriceExpressions(queryIntentSearchQuery);
        searchQuery = userProductSearchHashService.normalizeQuery(searchQuery);
        if (searchQuery.isBlank()) {
            return userProductSearchHashService.normalizeQuery(queryIntentSearchQuery).isBlank()
                    ? "products"
                    : userProductSearchHashService.normalizeQuery(queryIntentSearchQuery);
        }
        return searchQuery;
    }

    private CatalogSearchContext context(String country, String currency, String intent) {
        return new CatalogSearchContext(
                country,
                null,
                null,
                "en",
                currency,
                intent
        );
    }

    private CatalogSearchFilters filters(ParsedPrice parsedPrice, String currency) {
        if (parsedPrice == null) {
            return null;
        }
        return new CatalogSearchFilters(
                List.of(),
                new CatalogSearchPriceFilter(
                        minorUnits(parsedPrice.minAmount(), currency),
                        minorUnits(parsedPrice.maxAmount(), currency)
                )
        );
    }

    private ParsedPrice parsePrice(String originalQuery, String searchQuery) {
        ParsedPrice originalPrice = parsePrice(originalQuery);
        if (originalPrice != null) {
            return originalPrice;
        }
        return parsePrice(searchQuery);
    }

    private ParsedPrice parsePrice(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        ParsedPrice between = parseBetweenPrice(query);
        if (between != null) {
            return between;
        }
        ParsedPrice max = parseSingleBound(query, MAX_PRICE_PATTERN, "max");
        if (max != null) {
            return max;
        }
        max = parseSingleBound(query, MAX_PRICE_WITH_PREFIXED_CURRENCY_PATTERN, "max");
        if (max != null) {
            return max;
        }
        ParsedPrice min = parseSingleBound(query, MIN_PRICE_PATTERN, "min");
        if (min != null) {
            return min;
        }
        return parseSingleBound(query, MIN_PRICE_WITH_PREFIXED_CURRENCY_PATTERN, "min");
    }

    private ParsedPrice parseBetweenPrice(String query) {
        Matcher matcher = BETWEEN_PRICE_PATTERN.matcher(query);
        if (!matcher.find()) {
            return null;
        }
        String min = matcher.group("min");
        String max = matcher.group("max");
        String currency = firstPresent(
                currencyFromAmount(min),
                currencyFromAmount(max),
                currencyFromToken(group(matcher, "currency"))
        );
        return new ParsedPrice(amount(min), amount(max), currency);
    }

    private ParsedPrice parseSingleBound(String query, Pattern pattern, String bound) {
        Matcher matcher = pattern.matcher(query);
        if (!matcher.find()) {
            return null;
        }
        String amount = group(matcher, bound);
        String currency = firstPresent(
                currencyFromAmount(amount),
                currencyFromToken(group(matcher, "currency"))
        );
        if ("min".equals(bound)) {
            return new ParsedPrice(amount(amount), null, currency);
        }
        return new ParsedPrice(null, amount(amount), currency);
    }

    private String stripPriceExpressions(String value) {
        String stripped = value == null ? "" : value;
        for (Pattern pattern : PRICE_PATTERNS) {
            stripped = pattern.matcher(stripped).replaceAll(" ");
        }
        return stripped;
    }

    private String intent(
            String originalQuery,
            String searchQuery,
            ParsedPrice parsedPrice,
            ParsedPrice priceFilter,
            String currency,
            UserProductSearchQueryIntentResult queryIntent,
            UserSettingsResult settings
    ) {
        List<String> parts = new ArrayList<>();
        addPart(parts, "Original request: " + originalQuery);
        addPart(parts, "Catalog query: " + searchQuery);
        addPart(parts, priceIntent(parsedPrice, priceFilter, currency));
        addPart(parts, listPart("Hard constraints", queryIntent.constraints()));
        addPart(parts, listPart("Preference hints", queryIntent.preferenceHints()));
        addPart(parts, locationIntent(settings.location()));
        addPart(parts, activeFiltersIntent(settings.filters()));

        String intent = parts.stream()
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining("; "));
        return intent.length() <= MAX_INTENT_LENGTH ? intent : intent.substring(0, MAX_INTENT_LENGTH).trim();
    }

    private String priceIntent(ParsedPrice parsedPrice, ParsedPrice priceFilter, String currency) {
        if (priceFilter == null) {
            return null;
        }
        if (priceFilter.minAmount() != null && priceFilter.maxAmount() != null) {
            return "Hard price filter: between %s and %s%s"
                    .formatted(priceFilter.minAmount(), priceFilter.maxAmount(), valueSuffix(currency));
        }
        String prefix = parsedPrice == null ? "Hard budget price filter" : "Hard price filter";
        if (priceFilter.maxAmount() != null) {
            return "%s: at most %s%s"
                    .formatted(prefix, priceFilter.maxAmount(), valueSuffix(currency));
        }
        return "%s: at least %s%s"
                .formatted(prefix, priceFilter.minAmount(), valueSuffix(currency));
    }

    private String locationIntent(UserLocationResult location) {
        if (location == null) {
            return null;
        }
        return "User location signal: %s, %s (%s)".formatted(
                location.city(),
                location.country(),
                location.code()
        );
    }

    private String activeFiltersIntent(List<ShoppingFilterResult> filters) {
        if (safeList(filters).isEmpty()) {
            return null;
        }
        return "Active shopping preferences: " + safeList(filters).stream()
                .map(filter -> filter.label() + " - " + filter.description())
                .collect(Collectors.joining("; "));
    }

    private String listPart(String label, List<String> values) {
        if (safeList(values).isEmpty()) {
            return null;
        }
        return label + ": " + String.join(", ", safeList(values));
    }

    private String cacheKey(
            UserProductSearchQueryIntentResult queryIntent,
            String searchQuery,
            CatalogSearchContext context,
            CatalogSearchFilters filters
    ) {
        CatalogSearchPriceFilter price = filters == null ? null : filters.price();
        return String.join("\n",
                "intent=" + queryIntent.intentCacheKey(),
                "catalogQuery=" + searchQuery,
                "country=" + value(context.addressCountry()),
                "language=" + value(context.language()),
                "currency=" + value(context.currency()),
                "priceMin=" + value(price == null ? null : price.min()),
                "priceMax=" + value(price == null ? null : price.max())
        );
    }

    private Long minorUnits(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        return amount.movePointRight(currencyExponent(currency))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private BigDecimal amount(String amount) {
        String cleaned = normalizedDecimalAmount(amount);
        return new BigDecimal(cleaned);
    }

    private String normalizedDecimalAmount(String amount) {
        String cleaned = amount == null ? "" : amount.trim()
                .replaceAll("[^0-9,.\\-]", "");
        if (cleaned.contains(".") && cleaned.contains(",")) {
            int lastDot = cleaned.lastIndexOf('.');
            int lastComma = cleaned.lastIndexOf(',');
            cleaned = lastComma > lastDot
                    ? cleaned.replace(".", "").replace(',', '.')
                    : cleaned.replace(",", "");
        } else if (cleaned.contains(",")) {
            int commaIndex = cleaned.lastIndexOf(',');
            cleaned = cleaned.length() - commaIndex == 3
                    ? cleaned.replace(',', '.')
                    : cleaned.replace(",", "");
        } else if (cleaned.contains(".")) {
            int dotIndex = cleaned.lastIndexOf('.');
            if (cleaned.length() - dotIndex == 4) {
                cleaned = cleaned.replace(".", "");
            }
        }
        return cleaned;
    }

    private int currencyExponent(String currency) {
        return switch (value(currency).toUpperCase(Locale.ROOT)) {
            case "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF", "UGX", "VND",
                    "VUV", "XAF", "XOF", "XPF" -> 0;
            case "BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND" -> 3;
            default -> 2;
        };
    }

    private String countryCode(UserLocationResult location) {
        if (location == null) {
            return null;
        }
        String code = location.code();
        if (code != null && code.matches("(?i)[A-Z]{2}")) {
            return code.toUpperCase(Locale.ROOT);
        }
        String country = location.country() == null ? "" : location.country().trim().toLowerCase(Locale.ROOT);
        return switch (country) {
            case "united states", "usa", "us" -> "US";
            case "united kingdom", "uk", "great britain" -> "GB";
            case "czechia", "czech republic" -> "CZ";
            case "canada" -> "CA";
            case "australia" -> "AU";
            case "new zealand" -> "NZ";
            default -> null;
        };
    }

    private String defaultCurrency(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        return switch (countryCode.toUpperCase(Locale.ROOT)) {
            case "US" -> "USD";
            case "CA" -> "CAD";
            case "GB" -> "GBP";
            case "AU" -> "AUD";
            case "NZ" -> "NZD";
            case "CZ" -> "CZK";
            case "JP" -> "JPY";
            case "AT", "BE", "CY", "DE", "EE", "ES", "FI", "FR", "GR", "HR", "IE", "IT", "LT", "LU", "LV",
                    "MT", "NL", "PT", "SI", "SK" -> "EUR";
            default -> null;
        };
    }

    private String currencyFromAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        String trimmed = amount.trim();
        if (trimmed.startsWith("$")) {
            return "USD";
        }
        if (trimmed.startsWith("€")) {
            return "EUR";
        }
        if (trimmed.startsWith("£")) {
            return "GBP";
        }
        if (trimmed.startsWith("¥")) {
            return "JPY";
        }
        return null;
    }

    private String currencyFromToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("usd") || normalized.contains("dollar")) {
            return "USD";
        }
        if (normalized.startsWith("eur") || normalized.contains("euro")) {
            return "EUR";
        }
        if (normalized.startsWith("gbp") || normalized.contains("pound")) {
            return "GBP";
        }
        if (normalized.startsWith("czk") || normalized.contains("crown")) {
            return "CZK";
        }
        if (normalized.startsWith("cad") || normalized.contains("canadian")) {
            return "CAD";
        }
        if (normalized.startsWith("aud") || normalized.contains("australian")) {
            return "AUD";
        }
        if (normalized.startsWith("jpy") || normalized.contains("yen")) {
            return "JPY";
        }
        return null;
    }

    private String value(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String valueSuffix(String currency) {
        return currency == null || currency.isBlank() ? "" : " " + currency;
    }

    private String sanitizedSignal(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (sanitized.isBlank()) {
            return null;
        }
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }

    private String firstPresent(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private String firstPresent(String first, String second, String third) {
        String value = firstPresent(first, second);
        return value == null ? firstPresent(third, null) : value;
    }

    private void addPart(List<String> parts, String part) {
        if (part != null && !part.isBlank()) {
            parts.add(part.trim());
        }
    }

    private String group(Matcher matcher, String name) {
        try {
            return matcher.group(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record ParsedPrice(
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String currency
    ) {
    }
}
