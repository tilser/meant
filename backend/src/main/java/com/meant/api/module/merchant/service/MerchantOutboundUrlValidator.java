package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantOutboundUrlException;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MerchantOutboundUrlValidator {

    private final AddressResolver addressResolver;

    public MerchantOutboundUrlValidator() {
        this(host -> Arrays.asList(InetAddress.getAllByName(host)));
    }

    private MerchantOutboundUrlValidator(AddressResolver addressResolver) {
        this.addressResolver = addressResolver;
    }

    public static MerchantOutboundUrlValidator withResolver(AddressResolver addressResolver) {
        return new MerchantOutboundUrlValidator(addressResolver);
    }

    public URI validateMerchantUrl(String merchantDomain, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new MerchantOutboundUrlException("Merchant outbound URL is blank");
        }
        try {
            return validateMerchantUrl(merchantDomain, new URI(rawUrl.trim()));
        } catch (URISyntaxException exception) {
            throw new MerchantOutboundUrlException("Merchant outbound URL is malformed", exception);
        }
    }

    public URI validateMerchantUrl(String merchantDomain, URI uri) {
        validateAbsoluteHttpsUrl(uri);

        String normalizedDomain = normalizeDomain(merchantDomain);
        String normalizedHost = normalizeHost(uri.getHost(), "Merchant outbound URL host is invalid");
        if (!isAllowedMerchantHost(normalizedDomain, normalizedHost)) {
            throw new MerchantOutboundUrlException(
                    "Merchant outbound URL host is not under merchant domain " + normalizedDomain
            );
        }
        resolveAndValidatePublicHost(normalizedHost);
        return uri;
    }

    private void validateAbsoluteHttpsUrl(URI uri) {
        if (uri == null || !uri.isAbsolute() || uri.isOpaque()) {
            throw new MerchantOutboundUrlException("Merchant outbound URL must be absolute");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new MerchantOutboundUrlException("Merchant outbound URL must use https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new MerchantOutboundUrlException("Merchant outbound URL must include a host");
        }
        if (uri.getUserInfo() != null) {
            throw new MerchantOutboundUrlException("Merchant outbound URL must not include user info");
        }
    }

    private boolean isAllowedMerchantHost(String normalizedDomain, String normalizedHost) {
        return normalizedHost.equals(normalizedDomain) || normalizedHost.endsWith("." + normalizedDomain);
    }

    private void resolveAndValidatePublicHost(String normalizedHost) {
        List<InetAddress> addresses;
        try {
            addresses = addressResolver.resolve(normalizedHost);
        } catch (UnknownHostException exception) {
            throw new MerchantOutboundUrlException("Merchant outbound URL host could not be resolved", exception);
        }
        validatePublicAddresses(addresses);
    }

    void validatePublicAddresses(List<InetAddress> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            throw new MerchantOutboundUrlException("Merchant outbound URL host did not resolve to an address");
        }
        addresses.forEach(address -> {
            if (!isPublicAddress(address)) {
                throw new MerchantOutboundUrlException(
                        "Merchant outbound URL resolved to a non-public address: " + address.getHostAddress()
                );
            }
        });
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            return !isBlockedIpv4(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            return !isBlockedIpv6(address.getAddress());
        }
        return false;
    }

    private boolean isBlockedIpv4(byte[] address) {
        int first = unsigned(address[0]);
        int second = unsigned(address[1]);
        int third = unsigned(address[2]);

        return first == 0
                || first == 10
                || first == 100 && second >= 64 && second <= 127
                || first == 127
                || first == 169 && second == 254
                || first == 172 && second >= 16 && second <= 31
                || first == 192 && second == 0 && third == 0
                || first == 192 && second == 0 && third == 2
                || first == 192 && second == 168
                || first == 198 && (second == 18 || second == 19)
                || first == 198 && second == 51 && third == 100
                || first == 203 && second == 0 && third == 113
                || first >= 224;
    }

    private boolean isBlockedIpv6(byte[] address) {
        int first = unsigned(address[0]);
        int second = unsigned(address[1]);
        int third = unsigned(address[2]);
        int fourth = unsigned(address[3]);

        return isIpv4MappedAddress(address)
                || isIpv4CompatibleAddress(address)
                || isBlocked6to4Address(address)
                || (first & 0xfe) == 0xfc
                || first == 0xfe && (second & 0xc0) == 0x80
                || first == 0xff
                || first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8;
    }

    private boolean isIpv4MappedAddress(byte[] address) {
        for (int index = 0; index < 10; index++) {
            if (address[index] != 0) {
                return false;
            }
        }
        if (unsigned(address[10]) != 0xff || unsigned(address[11]) != 0xff) {
            return false;
        }
        return isBlockedIpv4(Arrays.copyOfRange(address, 12, 16));
    }

    private boolean isIpv4CompatibleAddress(byte[] address) {
        for (int index = 0; index < 12; index++) {
            if (address[index] != 0) {
                return false;
            }
        }
        byte[] embeddedIpv4 = Arrays.copyOfRange(address, 12, 16);
        return !isUnspecifiedOrLoopback(embeddedIpv4) && isBlockedIpv4(embeddedIpv4);
    }

    private boolean isUnspecifiedOrLoopback(byte[] embeddedIpv4) {
        return embeddedIpv4[0] == 0
                && embeddedIpv4[1] == 0
                && embeddedIpv4[2] == 0
                && (embeddedIpv4[3] == 0 || embeddedIpv4[3] == 1);
    }

    private boolean isBlocked6to4Address(byte[] address) {
        return unsigned(address[0]) == 0x20
                && unsigned(address[1]) == 0x02
                && isBlockedIpv4(Arrays.copyOfRange(address, 2, 6));
    }

    private String normalizeDomain(String merchantDomain) {
        String normalizedDomain = normalizeHost(merchantDomain, "Merchant domain is invalid");
        if (normalizedDomain.startsWith("www.")) {
            return normalizedDomain.substring("www.".length());
        }
        return normalizedDomain;
    }

    private String normalizeHost(String host, String errorMessage) {
        if (host == null || host.isBlank()) {
            throw new MerchantOutboundUrlException(errorMessage);
        }
        String normalizedHost = host.trim();
        if (normalizedHost.endsWith(".")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }
        try {
            return IDN.toASCII(normalizedHost, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new MerchantOutboundUrlException(errorMessage, exception);
        }
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    @FunctionalInterface
    public interface AddressResolver {

        List<InetAddress> resolve(String host) throws UnknownHostException;
    }
}
