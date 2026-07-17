package com.meant.api.module.user.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.user.constant.UserClothingFit;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryLocation;
import com.meant.api.module.user.exception.UnsupportedProductSearchCurrencyException;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserProductSearchCatalogInput;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchPriceFilter;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchSignals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class UserProductSearchCatalogInputBuilder {

    private static final String SEARCH_CURRENCY = "USD";
    private static final String AMOUNT_NUMBER_PATTERN =
            "(?:\\d{1,3}(?:[\\s\\.,]\\d{3})+|\\d+)(?:[\\.,]\\d{1,2})?";
    private static final String NON_USD_CURRENCY_SYMBOLS = "€£¥₹₩₽₺₴₫฿₱₪₦₲₵";
    private static final String AMOUNT_PATTERN = "(?:[$" + NON_USD_CURRENCY_SYMBOLS + "]\\s*)?"
            + AMOUNT_NUMBER_PATTERN;
    private static final String ISO_CURRENCY_CODE_PATTERN = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode)
            .sorted()
            .collect(Collectors.joining("|"));
    private static final String CURRENCY_PATTERN =
            "(?:usd|u\\.s\\. dollars?|us dollars?|dollars?|eur|euros?|gbp|pounds?|czk|kč|"
                    + "czech crowns?|crowns?|koruna|koruny|cad|canadian dollars?|aud|australian dollars?|"
                    + "nzd|new zealand dollars?|jpy|yen|chf|swiss francs?|sek|swedish kronor?|nok|"
                    + "norwegian kroner?|dkk|danish kroner?|pln|polish zloty|huf|ron|bgn|inr|indian rupees?|"
                    + "cny|rmb|chinese yuan|renminbi|hkd|hong kong dollars?|sgd|singapore dollars?|mxn|"
                    + "mexican pesos?|brl|brazilian reais?|zar|south african rand|krw|korean won|aed|sar|"
                    + "ils|thb|idr|myr|php|twd|(?-i:" + ISO_CURRENCY_CODE_PATTERN + "))";
    private static final String NON_USD_CURRENCY_SYMBOL_CLASS = "[" + NON_USD_CURRENCY_SYMBOLS + "]";
    private static final Pattern EXPLICIT_NON_USD_SYMBOL_AMOUNT_PATTERN = Pattern.compile(
            "(?:%s\\s*%s|%s\\s*%s)".formatted(
                    NON_USD_CURRENCY_SYMBOL_CLASS,
                    AMOUNT_NUMBER_PATTERN,
                    AMOUNT_NUMBER_PATTERN,
                    NON_USD_CURRENCY_SYMBOL_CLASS)
    );
    private static final Pattern PREFIXED_TEXT_CURRENCY_AMOUNT_PATTERN = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?<currency>%s)(?![\\p{L}\\p{N}])\\s+(?<amount>%s)"
                    .formatted(CURRENCY_PATTERN, AMOUNT_NUMBER_PATTERN),
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SUFFIXED_TEXT_CURRENCY_AMOUNT_PATTERN = Pattern.compile(
            "(?<!\\d)(?<amount>%s)\\s+(?<currency>%s)(?![\\p{L}\\p{N}])"
                    .formatted(AMOUNT_NUMBER_PATTERN, CURRENCY_PATTERN),
            Pattern.CASE_INSENSITIVE
    );
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
    private static final Pattern TRAILING_CURRENCY_PATTERN = Pattern.compile(
            "^\\s*(?<currency>%s)(?![\\p{L}\\p{N}])".formatted(CURRENCY_PATTERN),
            Pattern.CASE_INSENSITIVE
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
        return build(originalQuery, queryIntent, settings, null, null, null);
    }

    public UserProductSearchCatalogInput build(
            String originalQuery,
            UserProductSearchQueryIntentResult queryIntent,
            UserSettingsResult settings,
            String buyerIp,
            String userAgent
    ) {
        return build(originalQuery, queryIntent, settings, buyerIp, userAgent, null);
    }

    public UserProductSearchCatalogInput build(
            String originalQuery,
            UserProductSearchQueryIntentResult queryIntent,
            UserSettingsResult settings,
            String buyerIp,
            String userAgent,
            CatalogDiscoveryFilters qualifiedFilters
    ) {
        validateSupportedCurrency(originalQuery);
        ParsedPrice parsedPrice = parsePrice(originalQuery, queryIntent.searchQuery());
        validateCurrency(parsedPrice);
        ParsedPrice priceFilter = qualifiedFilters == null
                ? parsedPrice
                : parsedPrice(qualifiedFilters);
        String searchQuery = searchQuery(queryIntent.searchQuery(), parsedPrice);
        CatalogDiscoveryLocation shipsTo = qualifiedFilters == null ? null : qualifiedFilters.shipsTo();
        String country = shipsTo != null
                ? shipsTo.country()
                : countryCode(settings.location());
        String currency = SEARCH_CURRENCY;
        CatalogSearchContext context = context(
                country,
                shipsTo == null ? null : shipsTo.region(),
                shipsTo == null ? null : shipsTo.postalCode(),
                currency,
                intent(
                        originalQuery,
                        searchQuery,
                        parsedPrice,
                        priceFilter,
                        currency,
                        queryIntent,
                        qualifiedFilters == null ? settings : null
                )
        );
        CatalogSearchSignals signals = signals(buyerIp, userAgent);
        CatalogSearchFilters filters = filters(priceFilter, qualifiedFilters);
        return new UserProductSearchCatalogInput(
                searchQuery,
                cacheKey(queryIntent, searchQuery, context, filters, qualifiedFilters),
                context,
                signals,
                filters,
                qualifiedFilters
        );
    }

    /** Rejects explicitly stated non-USD prices before an LLM can rewrite them out of the catalog query. */
    public void validateSupportedCurrency(String value) {
        rejectExplicitNonUsdAmount(value);
        ParsedPrice parsedPrice = parsePrice(value);
        if (parsedPrice == null && value != null && !value.isBlank()) {
            parsedPrice = parsePrice("under " + value.trim());
        }
        validateCurrency(parsedPrice);
    }

    private void rejectExplicitNonUsdAmount(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (EXPLICIT_NON_USD_SYMBOL_AMOUNT_PATTERN.matcher(value).find()
                || containsNonUsdTextCurrency(PREFIXED_TEXT_CURRENCY_AMOUNT_PATTERN.matcher(value))
                || containsNonUsdTextCurrency(SUFFIXED_TEXT_CURRENCY_AMOUNT_PATTERN.matcher(value))) {
            throw new UnsupportedProductSearchCurrencyException("NON_USD");
        }
    }

    private boolean containsNonUsdTextCurrency(Matcher matcher) {
        while (matcher.find()) {
            if (!SEARCH_CURRENCY.equalsIgnoreCase(currencyFromToken(matcher.group("currency")))) {
                return true;
            }
        }
        return false;
    }

    private CatalogSearchSignals signals(String buyerIp, String userAgent) {
        String sanitizedBuyerIp = sanitizedSignal(buyerIp, 128);
        String sanitizedUserAgent = sanitizedSignal(userAgent, 512);
        if (sanitizedBuyerIp == null && sanitizedUserAgent == null) {
            return null;
        }
        return new CatalogSearchSignals(sanitizedBuyerIp, sanitizedUserAgent);
    }

    private ParsedPrice parsedPrice(CatalogDiscoveryFilters filters) {
        if (filters.price() == null) {
            return null;
        }
        return new ParsedPrice(
                majorUnits(filters.price().min()),
                majorUnits(filters.price().max()),
                SEARCH_CURRENCY
        );
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

    private CatalogSearchContext context(
            String country,
            String region,
            String postalCode,
            String currency,
            String intent
    ) {
        return new CatalogSearchContext(
                country,
                region,
                postalCode,
                "en",
                currency,
                intent
        );
    }

    private CatalogSearchFilters filters(
            ParsedPrice parsedPrice,
            CatalogDiscoveryFilters qualifiedFilters
    ) {
        List<String> categories = qualifiedFilters == null
                ? List.of()
                : qualifiedFilters.categoryIds();
        if (parsedPrice == null && categories.isEmpty()) {
            return null;
        }
        return new CatalogSearchFilters(
                categories,
                parsedPrice == null ? null : new CatalogSearchPriceFilter(
                        minorUnits(parsedPrice.minAmount()),
                        minorUnits(parsedPrice.maxAmount())
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
        String currency = parsedCurrency(
                currencyFromAmount(min),
                currencyFromAmount(max),
                currencyFromToken(firstPresent(
                        group(matcher, "currency"),
                        trailingCurrencyToken(query, matcher.end("max"))
                ))
        );
        return new ParsedPrice(amount(min), amount(max), currency);
    }

    private ParsedPrice parseSingleBound(String query, Pattern pattern, String bound) {
        Matcher matcher = pattern.matcher(query);
        if (!matcher.find()) {
            return null;
        }
        String amount = group(matcher, bound);
        String currency = parsedCurrency(
                currencyFromAmount(amount),
                currencyFromToken(firstPresent(
                        group(matcher, "currency"),
                        trailingCurrencyToken(query, matcher.end(bound))
                ))
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
        if (settings != null) {
            addPart(parts, locationsIntent(settings.locations()));
            addPart(parts, clothingFitIntent(settings.clothingFit()));
            addPart(parts, activeFiltersIntent(settings.filters()));
        }

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

    private String locationsIntent(List<UserLocationResult> locations) {
        if (safeList(locations).isEmpty()) {
            return null;
        }
        return "User delivery location signals: " + safeList(locations).stream()
                .map(location -> "%s, %s (%s)".formatted(
                        location.city(),
                        location.country(),
                        location.code()))
                .collect(Collectors.joining("; "));
    }

    private String clothingFitIntent(String clothingFit) {
        return UserClothingFit.fromValue(clothingFit)
                .map(fit -> switch (fit) {
                    case MEN -> "Hard apparel audience filter: men's sizing; "
                            + "exclude women's and children's apparel when audience is known";
                    case WOMEN -> "Hard apparel audience filter: women's sizing; "
                            + "exclude men's and children's apparel when audience is known";
                    case OTHER -> "Clothing fit signal: prefer " + fit.label() + " for apparel and footwear";
                })
                .orElse(null);
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
            CatalogSearchFilters filters,
            CatalogDiscoveryFilters discoveryFilters
    ) {
        CatalogSearchPriceFilter price = filters == null ? null : filters.price();
        return String.join("\n",
                "intent=" + queryIntent.intentCacheKey(),
                "catalogQuery=" + searchQuery,
                "country=" + value(context.addressCountry()),
                "language=" + value(context.language()),
                "currency=" + value(context.currency()),
                "priceMin=" + value(price == null ? null : price.min()),
                "priceMax=" + value(price == null ? null : price.max()),
                "discoveryFilters=" + value(discoveryFilters)
        );
    }

    private BigDecimal majorUnits(Long amount) {
        return amount == null ? null : BigDecimal.valueOf(amount, 2);
    }

    private Long minorUnits(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.movePointRight(2)
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

    private String currencyFromAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        String trimmed = amount.trim();
        if (trimmed.startsWith("$")) {
            return "USD";
        }
        if (!trimmed.isEmpty() && NON_USD_CURRENCY_SYMBOLS.indexOf(trimmed.charAt(0)) >= 0) {
            return "NON_USD";
        }
        return null;
    }

    private String currencyFromToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("usd")
                || normalized.matches("u\\.s\\. dollars?")
                || normalized.matches("us dollars?")
                || normalized.matches("dollars?")) {
            return "USD";
        }
        return "NON_USD";
    }

    private String parsedCurrency(String... currencies) {
        for (String currency : currencies) {
            if (currency != null && !SEARCH_CURRENCY.equalsIgnoreCase(currency)) {
                return currency;
            }
        }
        for (String currency : currencies) {
            if (currency != null && !currency.isBlank()) {
                return currency;
            }
        }
        return null;
    }

    private void validateCurrency(ParsedPrice parsedPrice) {
        if (parsedPrice == null || parsedPrice.currency() == null) {
            return;
        }
        if (!SEARCH_CURRENCY.equalsIgnoreCase(parsedPrice.currency())) {
            throw new UnsupportedProductSearchCurrencyException(parsedPrice.currency());
        }
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

    private String trailingCurrencyToken(String query, int amountEnd) {
        if (query == null || amountEnd < 0 || amountEnd >= query.length()) {
            return null;
        }
        Matcher matcher = TRAILING_CURRENCY_PATTERN.matcher(query.substring(amountEnd));
        return matcher.find() ? matcher.group("currency") : null;
    }

    private record ParsedPrice(
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String currency
    ) {
    }
}
