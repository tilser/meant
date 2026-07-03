package com.meant.api.module.review.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.review.exception.ReviewException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewOutboundUrlValidatorTest {

    @Test
    void allowsAbsoluteHttpsUrlResolvingToPublicAddress() {
        ReviewOutboundUrlValidator validator = ReviewOutboundUrlValidator.withResolver(
                _ -> List.of(InetAddress.getByName("93.184.216.34"))
        );

        assertThatCode(() -> validator.validateOutboundUrl(URI.create("https://merchant.example/")))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksAbsoluteHttpsUrlResolvingToLocalhost() {
        ReviewOutboundUrlValidator validator = ReviewOutboundUrlValidator.withResolver(
                _ -> List.of(InetAddress.getByName("127.0.0.1"))
        );

        assertThatThrownBy(() -> validator.validateOutboundUrl(URI.create("https://merchant.example/")))
                .isInstanceOf(ReviewException.class)
                .hasMessageContaining("non-public address");
    }

    @Test
    void blocksNonHttpsUrl() {
        ReviewOutboundUrlValidator validator = ReviewOutboundUrlValidator.withResolver(
                _ -> List.of(InetAddress.getByName("93.184.216.34"))
        );

        assertThatThrownBy(() -> validator.validateOutboundUrl(URI.create("http://merchant.example/")))
                .isInstanceOf(ReviewException.class)
                .hasMessageContaining("must use https");
    }
}
