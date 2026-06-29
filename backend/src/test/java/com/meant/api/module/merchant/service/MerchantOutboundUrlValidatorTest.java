package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.merchant.exception.MerchantOutboundUrlException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MerchantOutboundUrlValidatorTest {

    @Test
    void allowsHttpsUrlOnMerchantDomainResolvingToPublicAddress() {
        MerchantOutboundUrlValidator validator = validatorResolvingTo("93.184.216.34");

        URI uri = validator.validateMerchantUrl("merchant.example", "https://api.merchant.example/api/mcp");

        assertThat(uri).isEqualTo(URI.create("https://api.merchant.example/api/mcp"));
    }

    @Test
    void allowsDelegatedOutboundUrlOutsideMerchantDomain() {
        MerchantOutboundUrlValidator validator = validatorResolvingTo("93.184.216.34");

        URI uri = validator.validateOutboundUrl("https://merchant.myshopify.com/api/ucp/mcp");

        assertThat(uri).isEqualTo(URI.create("https://merchant.myshopify.com/api/ucp/mcp"));
    }

    @Test
    void rejectsNonHttpsUrl() {
        MerchantOutboundUrlValidator validator = validatorResolvingTo("93.184.216.34");

        assertThatThrownBy(() -> validator.validateMerchantUrl("merchant.example", "http://merchant.example/api/mcp"))
                .isInstanceOf(MerchantOutboundUrlException.class)
                .hasMessageContaining("https");
    }

    @Test
    void rejectsHostOutsideMerchantDomain() {
        MerchantOutboundUrlValidator validator = validatorResolvingTo("93.184.216.34");

        assertThatThrownBy(() -> validator.validateMerchantUrl("merchant.example", "https://evil.example/api/mcp"))
                .isInstanceOf(MerchantOutboundUrlException.class)
                .hasMessageContaining("not under merchant domain");
    }

    @Test
    void rejectsLocalhostResolvingToLoopbackAddress() {
        MerchantOutboundUrlValidator validator = validatorResolvingTo("127.0.0.1");

        assertThatThrownBy(() -> validator.validateMerchantUrl("localhost", "https://localhost/api/mcp"))
                .isInstanceOf(MerchantOutboundUrlException.class)
                .hasMessageContaining("non-public");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0",
            "10.0.0.1",
            "100.64.0.1",
            "127.0.0.1",
            "169.254.169.254",
            "172.16.0.1",
            "192.0.0.1",
            "192.0.2.1",
            "192.168.1.1",
            "198.18.0.1",
            "198.51.100.1",
            "203.0.113.1",
            "224.0.0.1",
            "::1",
            "fc00::1",
            "fe80::1",
            "ff02::1",
            "::ffff:10.0.0.1",
            "::10.0.0.1",
            "2002:0a00:0001::1"
    })
    void rejectsNonPublicResolvedAddresses(String address) {
        MerchantOutboundUrlValidator validator = validatorResolvingTo(address);

        assertThatThrownBy(() -> validator.validateMerchantUrl("merchant.example", "https://merchant.example/api/mcp"))
                .isInstanceOf(MerchantOutboundUrlException.class)
                .hasMessageContaining("non-public");
    }

    @Test
    void allowsPublicAddressInNarrowNineteenTwoRange() {
        MerchantOutboundUrlValidator validator = validatorResolvingTo("192.0.3.1");

        URI uri = validator.validateMerchantUrl("merchant.example", "https://merchant.example/api/mcp");

        assertThat(uri).isEqualTo(URI.create("https://merchant.example/api/mcp"));
    }

    @Test
    void rejectsIpv6LiteralHostsBecauseMerchantEndpointsMustUseMerchantDomains() {
        MerchantOutboundUrlValidator validator = validatorResolvingTo("2001:4860:4860::8888");

        assertThatThrownBy(() -> validator.validateMerchantUrl(
                "merchant.example",
                "https://[2001:4860:4860::8888]/api/mcp"
        ))
                .isInstanceOf(MerchantOutboundUrlException.class)
                .hasMessageContaining("host is invalid");
    }

    private MerchantOutboundUrlValidator validatorResolvingTo(String address) {
        return MerchantOutboundUrlValidator.withResolver(host -> List.of(InetAddress.getByName(address)));
    }
}
