package com.meant.api.module.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ReviewProductPageLinkExtractorTest {

    private final ReviewProductPageLinkExtractor extractor = new ReviewProductPageLinkExtractor();

    @Test
    void skipsMalformedProductLinksAndContinuesSearching() {
        String html = """
                <a href="/products/[broken]">Broken</a>
                <a href="/products/good-shirt">Good shirt</a>
                """;

        assertThat(extractor.firstProductPage(URI.create("https://merchant.example/"), html))
                .contains(URI.create("https://merchant.example/products/good-shirt"));
    }

    @Test
    void ignoresCrossHostProductLinks() {
        String html = "<a href=\"https://other.example/products/good-shirt\">Other shop</a>";

        assertThat(extractor.firstProductPage(URI.create("https://merchant.example/"), html))
                .isEmpty();
    }

    @Test
    void ignoresProductLinksOnParentSuffixHosts() {
        String html = "<a href=\"https://com/products/good-shirt\">Invalid parent host</a>";

        assertThat(extractor.firstProductPage(URI.create("https://example.com/"), html))
                .isEmpty();
    }

    @Test
    void allowsProductLinksOnWwwCanonicalHost() {
        String html = "<a href=\"https://example.com/products/good-shirt\">Good shirt</a>";

        assertThat(extractor.firstProductPage(URI.create("https://www.example.com/"), html))
                .contains(URI.create("https://example.com/products/good-shirt"));
    }

    @Test
    void allowsProductLinksOnCanonicalSubdomains() {
        String html = "<a href=\"https://us.checkout.gymshark.com/products/good-shirt\">Good shirt</a>";

        assertThat(extractor.firstProductPage(URI.create("https://gymshark.com/"), html))
                .contains(URI.create("https://us.checkout.gymshark.com/products/good-shirt"));
    }
}
