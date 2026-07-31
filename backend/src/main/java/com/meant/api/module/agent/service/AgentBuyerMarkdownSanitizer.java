package com.meant.api.module.agent.service;

import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps buyer-visible Markdown readable after transport coordinates are neutralized. */
public final class AgentBuyerMarkdownSanitizer {

    private static final Pattern NEUTRALIZED_MARKDOWN_LINK = Pattern.compile(
            "(?<!!)\\[([^\\n]+?)]\\([^\\n)]*\\bthe merchant\\b[^\\n)]*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private AgentBuyerMarkdownSanitizer() {
    }

    public static String sanitize(String value) {
        return collapseNeutralizedLinks(MerchantBuyerTextSanitizer.sanitize(value));
    }

    static String collapseNeutralizedLinks(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Matcher matcher = NEUTRALIZED_MARKDOWN_LINK.matcher(value);
        StringBuilder sanitized = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sanitized, Matcher.quoteReplacement(matcher.group(1)));
        }
        matcher.appendTail(sanitized);
        return sanitized.toString();
    }
}
