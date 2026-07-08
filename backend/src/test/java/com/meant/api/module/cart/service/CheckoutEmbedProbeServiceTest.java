package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.MerchantOutboundUrlValidator;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class CheckoutEmbedProbeServiceTest {

    @Test
    void frameAncestorsNoneIsNotEmbeddable() {
        CheckoutEmbedProbeService service = service(response(
                "https://merchant.example/checkout",
                200,
                Map.of("content-security-policy", List.of("block-all-mixed-content; frame-ancestors 'none';"))
        ));

        assertThat(service.embeddable("https://merchant.example/checkout")).isFalse();
    }

    @Test
    void missingFramingHeadersIsEmbeddable() {
        CheckoutEmbedProbeService service = service(response(
                "https://merchant.example/checkout",
                200,
                Map.of("content-type", List.of("text/html"))
        ));

        assertThat(service.embeddable("https://merchant.example/checkout")).isTrue();
    }

    @Test
    void xFrameOptionsDenyIsNotEmbeddable() {
        CheckoutEmbedProbeService service = service(response(
                "https://merchant.example/checkout",
                200,
                Map.of("x-frame-options", List.of("DENY"))
        ));

        assertThat(service.embeddable("https://merchant.example/checkout")).isFalse();
    }

    @Test
    void followsRedirectAndUsesFinalResponseHeaders() {
        CheckoutEmbedProbeService service = service(
                response(
                        "https://merchant.myshopify.example/cart/c/token",
                        301,
                        Map.of("location", List.of("https://merchant.example/cart/c/token"))
                ),
                response(
                        "https://merchant.example/cart/c/token",
                        200,
                        Map.of("content-security-policy", List.of("frame-ancestors 'none'"))
                )
        );

        assertThat(service.embeddable("https://merchant.myshopify.example/cart/c/token")).isFalse();
    }

    @Test
    void blockingRedirectHeaderShortCircuitsBeforeFollowing() {
        CheckoutEmbedProbeService service = service(response(
                "https://merchant.example/checkout",
                302,
                Map.of(
                        "location", List.of("https://elsewhere.example/checkout"),
                        "content-security-policy", List.of("frame-ancestors 'none'")
                )
        ));

        assertThat(service.embeddable("https://merchant.example/checkout")).isFalse();
    }

    @Test
    void probeFailureReturnsUnknown() {
        CheckoutEmbedProbeService service = new CheckoutEmbedProbeService(
                validator(),
                new StubHttpClient(null)
        );

        assertThat(service.embeddable("https://merchant.example/checkout")).isNull();
    }

    @Test
    void cachesResultsPerUrl() {
        StubHttpClient httpClient = new StubHttpClient(new ArrayDeque<>(List.of(response(
                "https://merchant.example/checkout",
                200,
                Map.of("x-frame-options", List.of("DENY"))
        ))));
        CheckoutEmbedProbeService service = new CheckoutEmbedProbeService(validator(), httpClient);

        assertThat(service.embeddable("https://merchant.example/checkout")).isFalse();
        assertThat(service.embeddable("https://merchant.example/checkout")).isFalse();
        assertThat(httpClient.requestCount).isEqualTo(1);
    }

    private CheckoutEmbedProbeService service(HttpResponse<Void>... responses) {
        return new CheckoutEmbedProbeService(
                validator(),
                new StubHttpClient(new ArrayDeque<>(List.of(responses)))
        );
    }

    private MerchantOutboundUrlValidator validator() {
        return MerchantOutboundUrlValidator.withResolver(host -> {
            try {
                return List.of(InetAddress.getByName("93.184.216.34"));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private HttpResponse<Void> response(String url, int status, Map<String, List<String>> headers) {
        return new StubResponse(URI.create(url), status, HttpHeaders.of(headers, (name, value) -> true));
    }

    private record StubResponse(URI uri, int statusCode, HttpHeaders headers) implements HttpResponse<Void> {

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(uri).build();
        }

        @Override
        public Optional<HttpResponse<Void>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public Void body() {
            return null;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private static class StubHttpClient extends HttpClient {

        private final Deque<HttpResponse<Void>> responses;
        private int requestCount;

        StubHttpClient(Deque<HttpResponse<Void>> responses) {
            this.responses = responses;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            requestCount++;
            if (responses == null || responses.isEmpty()) {
                throw new IOException("probe failed");
            }
            return (HttpResponse<T>) responses.poll();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<java.net.CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<java.time.Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<java.net.ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public javax.net.ssl.SSLContext sslContext() {
            try {
                return javax.net.ssl.SSLContext.getDefault();
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public javax.net.ssl.SSLParameters sslParameters() {
            return new javax.net.ssl.SSLParameters();
        }

        @Override
        public Optional<java.net.Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<java.util.concurrent.Executor> executor() {
            return Optional.empty();
        }
    }
}
