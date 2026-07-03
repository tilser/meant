package com.meant.api.module.review.service;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ReviewProductPageLinkExtractor {

    private static final Pattern PRODUCT_LINK_PATTERN = Pattern.compile(
            "href\\s*=\\s*[\"']([^\"']*/products/[^\"'#?]+(?:\\?[^\"'#]*)?)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    public Optional<URI> firstProductPage(URI storefrontBaseUri, String html) {
        if (storefrontBaseUri == null || html == null || html.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = PRODUCT_LINK_PATTERN.matcher(html);
        while (matcher.find()) {
            Optional<URI> productUri = productUri(storefrontBaseUri, matcher.group(1));
            if (productUri.isPresent()) {
                return productUri;
            }
        }
        return Optional.empty();
    }

    private Optional<URI> productUri(URI storefrontBaseUri, String rawHref) {
        if (rawHref == null || rawHref.isBlank()) {
            return Optional.empty();
        }
        String href = rawHref.trim()
                .replace("&amp;", "&");
        try {
            URI resolved = storefrontBaseUri.resolve(href);
            if (!"https".equalsIgnoreCase(resolved.getScheme())) {
                return Optional.empty();
            }
            if (resolved.getHost() == null || !resolved.getHost().equalsIgnoreCase(storefrontBaseUri.getHost())) {
                return Optional.empty();
            }
            return Optional.of(resolved);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
