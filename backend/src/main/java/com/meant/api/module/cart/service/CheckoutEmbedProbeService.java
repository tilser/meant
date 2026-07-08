package com.meant.api.module.cart.service;

import com.meant.api.module.merchant.exception.MerchantOutboundUrlException;
import com.meant.api.module.merchant.service.MerchantOutboundUrlValidator;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Probes whether a merchant checkout URL can be rendered inside an iframe.
 *
 * <p>Merchant checkouts commonly answer with {@code Content-Security-Policy: frame-ancestors
 * 'none'} (or {@code X-Frame-Options}), which the browser cannot detect from script — the frame
 * just renders an opaque error page. Probing the response headers server-side lets the client
 * decide between an embedded frame and an open-in-new-tab handoff before rendering anything.
 */
@Service
@Slf4j
public class CheckoutEmbedProbeService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int MAX_REDIRECT_HOPS = 3;
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final MerchantOutboundUrlValidator outboundUrlValidator;
    private final HttpClient httpClient;
    private final Map<String, CachedProbe> cache = new ConcurrentHashMap<>();

    @Autowired
    public CheckoutEmbedProbeService(MerchantOutboundUrlValidator outboundUrlValidator) {
        this(outboundUrlValidator, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(REQUEST_TIMEOUT)
                .build());
    }

    CheckoutEmbedProbeService(MerchantOutboundUrlValidator outboundUrlValidator, HttpClient httpClient) {
        this.outboundUrlValidator = outboundUrlValidator;
        this.httpClient = httpClient;
    }

    /**
     * @return {@code true} when the URL can be framed, {@code false} when the merchant forbids
     *         framing, {@code null} when the probe could not determine it (client may attempt).
     */
    public Boolean embeddable(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String key = url.trim();
        CachedProbe cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.embeddable();
        }
        Boolean result = probe(key);
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(key, new CachedProbe(result, Instant.now().plus(CACHE_TTL)));
        return result;
    }

    private Boolean probe(String url) {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {
            HttpResponse<Void> response;
            try {
                URI target = outboundUrlValidator.validateOutboundUrl(current);
                response = httpClient.send(
                        HttpRequest.newBuilder(target)
                                .timeout(REQUEST_TIMEOUT)
                                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                                .build(),
                        HttpResponse.BodyHandlers.discarding()
                );
            } catch (MerchantOutboundUrlException exception) {
                return false;
            } catch (IOException exception) {
                log.debug("Checkout embed probe failed for {}: {}", current, exception.getMessage());
                return null;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }

            Boolean framing = framingAllowed(response.headers().map());
            if (!framing) {
                return false;
            }
            String location = redirectLocation(response);
            if (location == null) {
                if (response.statusCode() == 405 || response.statusCode() == 501) {
                    // Server rejects HEAD; framing headers are unreliable here.
                    return null;
                }
                return true;
            }
            current = location;
        }
        return null;
    }

    private String redirectLocation(HttpResponse<Void> response) {
        int status = response.statusCode();
        if (status < 300 || status > 399) {
            return null;
        }
        String location = response.headers().firstValue("location").orElse(null);
        if (location == null || location.isBlank()) {
            return null;
        }
        return response.uri().resolve(location.trim()).toString();
    }

    private Boolean framingAllowed(Map<String, List<String>> headers) {
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            String name = header.getKey() == null ? "" : header.getKey().toLowerCase(Locale.ROOT);
            if ("content-security-policy".equals(name)) {
                for (String value : header.getValue()) {
                    String frameAncestors = frameAncestorsDirective(value);
                    if (frameAncestors != null && !frameAncestors.contains("*")) {
                        return false;
                    }
                }
            }
            if ("x-frame-options".equals(name)) {
                for (String value : header.getValue()) {
                    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
                    if (normalized.startsWith("deny") || normalized.startsWith("sameorigin")) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private String frameAncestorsDirective(String policy) {
        if (policy == null) {
            return null;
        }
        for (String directive : policy.split(";")) {
            String trimmed = directive.trim().toLowerCase(Locale.ROOT);
            if (trimmed.startsWith("frame-ancestors")) {
                return trimmed.substring("frame-ancestors".length()).trim();
            }
        }
        return null;
    }

    private record CachedProbe(Boolean embeddable, Instant expiresAt) {
    }
}
