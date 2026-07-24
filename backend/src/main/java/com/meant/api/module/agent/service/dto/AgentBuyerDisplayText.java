package com.meant.api.module.agent.service.dto;

import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
import com.meant.api.module.merchant.service.MerchantProductMessageSanitizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps provider transport labels out of model- and buyer-visible agent projections. */
final class AgentBuyerDisplayText {

    private static final String NEUTRAL_MERCHANT = "Merchant";
    private static final Pattern SHOPIFY_TECHNICAL_HOST = Pattern.compile(
            "(?<![A-Z0-9.-])(?:https?://)?(?:[A-Z0-9-]+\\.)*myshopify\\.com"
                    + "(?::\\d{1,5})?(?:/[^\\s]*)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MCP_TECHNICAL_HOST = Pattern.compile(
            "(?<![A-Z0-9.-])(?:https?://)?"
                    + "(?:mcp\\.[A-Z0-9.-]+|[A-Z0-9.-]+\\.mcp\\.[A-Z0-9.-]+)"
                    + "(?::\\d{1,5})?(?:/[^\\s]*)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private AgentBuyerDisplayText() {
    }

    static String text(String value, List<String> technicalDomains) {
        return text(value, new Context(null, technicalDomains));
    }

    static String text(String value, Context context) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Context effectiveContext = context == null ? new Context(null, List.of()) : context;
        String sanitized = MerchantBuyerTextSanitizer.sanitize(
                value,
                effectiveContext.merchantOrigin(),
                null,
                (String) null
        );
        for (String domain : effectiveContext.technicalAliases()) {
            sanitized = replaceTechnicalDomainUrls(
                    sanitized,
                    domain,
                    effectiveContext.replacement()
            );
            sanitized = MerchantBuyerTextSanitizer.sanitize(
                    sanitized,
                    effectiveContext.merchantOrigin(),
                    domain,
                    (String) null
            );
        }
        sanitized = SHOPIFY_TECHNICAL_HOST.matcher(sanitized)
                .replaceAll(Matcher.quoteReplacement(effectiveContext.replacement()));
        return MCP_TECHNICAL_HOST.matcher(sanitized)
                .replaceAll(Matcher.quoteReplacement(effectiveContext.replacement()));
    }

    static String label(String value, List<String> technicalDomains) {
        return label(value, new Context(null, technicalDomains));
    }

    static String label(String value, Context context) {
        String sanitized = text(value, context);
        if (sanitized == null || sanitized.isBlank()) {
            return sanitized;
        }
        String trimmed = sanitized.trim();
        return trimmed.equalsIgnoreCase("the merchant") ? NEUTRAL_MERCHANT : trimmed;
    }

    static String safeUrl(String value, List<String> technicalDomains) {
        return safeUrl(value, new Context(null, technicalDomains));
    }

    static String safeUrl(String value, Context context) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        return MerchantProductMessageSanitizer.buyerSafeUrl(
                trimmed,
                MerchantProductMessageSanitizer.context(
                        context == null ? null : context.merchantOrigin(),
                        null,
                        context == null
                                ? new String[0]
                                : context.technicalAliases().toArray(String[]::new)
                )
        );
    }

    static Context context(String merchantOrigin, List<String> technicalAliases) {
        return new Context(merchantOrigin, technicalAliases);
    }

    record Context(String merchantOrigin, List<String> technicalAliases) {

        Context {
            merchantOrigin = merchantOrigin == null || merchantOrigin.isBlank()
                    ? null
                    : merchantOrigin.trim();
            merchantOrigin = MerchantBuyerTextSanitizer.buyerSafeMerchantOrigin(merchantOrigin);
            List<String> aliases = new ArrayList<>();
            if (technicalAliases != null) {
                technicalAliases.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .distinct()
                        .forEach(aliases::add);
            }
            technicalAliases = List.copyOf(aliases);
        }

        String replacement() {
            return merchantOrigin == null ? "the merchant" : merchantOrigin;
        }
    }

    private static String replaceTechnicalDomainUrls(
            String value,
            String domain,
            String replacement
    ) {
        if (domain == null || domain.isBlank()) {
            return value;
        }
        Pattern url = Pattern.compile(
                "(?<![A-Z0-9.-])(?:https?://)?"
                        + Pattern.quote(domain.trim())
                        + "(?::\\d{1,5})?(?![A-Z0-9.-])(?:/[^\\s<>{}\\[\\]\"']*)?",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        return url.matcher(value).replaceAll(Matcher.quoteReplacement(replacement));
    }
}
