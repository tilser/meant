package com.meant.api.plugin.transport.client;

import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.transport.dto.ShopifyUcpRequestOptions;
import jakarta.annotation.PreDestroy;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ShopifyAuthenticatedUcpClient implements ShopifyUcpClient {

    private final RestClient.Builder restClientBuilder;
    private final RestClient fixedRestClient;
    private final UcpMcpClient ucpMcpClient;
    private final ShopifyBearerAuthenticationStrategy authenticationStrategy;
    private final ExecutorService executor;
    private final Clock clock;

    @Autowired
    public ShopifyAuthenticatedUcpClient(
            RestClient.Builder restClientBuilder,
            UcpMcpClient ucpMcpClient,
            ShopifyBearerAuthenticationStrategy authenticationStrategy
    ) {
        this(restClientBuilder, null, ucpMcpClient, authenticationStrategy,
                Executors.newVirtualThreadPerTaskExecutor(), Clock.systemUTC());
    }

    ShopifyAuthenticatedUcpClient(
            RestClient fixedRestClient,
            UcpMcpClient ucpMcpClient,
            ShopifyBearerAuthenticationStrategy authenticationStrategy,
            ExecutorService executor,
            Clock clock
    ) {
        this(null, fixedRestClient, ucpMcpClient, authenticationStrategy, executor, clock);
    }

    private ShopifyAuthenticatedUcpClient(
            RestClient.Builder restClientBuilder,
            RestClient fixedRestClient,
            UcpMcpClient ucpMcpClient,
            ShopifyBearerAuthenticationStrategy authenticationStrategy,
            ExecutorService executor,
            Clock clock
    ) {
        this.restClientBuilder = restClientBuilder;
        this.fixedRestClient = fixedRestClient;
        this.ucpMcpClient = Objects.requireNonNull(ucpMcpClient, "ucpMcpClient");
        this.authenticationStrategy = Objects.requireNonNull(authenticationStrategy, "authenticationStrategy");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public UcpToolResponse callTool(ShopifyUcpRequestOptions options, String toolName, Object arguments) {
        Objects.requireNonNull(options, "options");
        Future<UcpToolResponse> call = executor.submit(
                () -> callWithSingleUnauthorizedRefresh(restClient(options), options, toolName, arguments)
        );
        try {
            return call.get(options.requestDeadline().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            call.cancel(true);
            throw failure(ShopifyUcpTransportFailure.TIMEOUT, "Shopify UCP request exceeded its deadline", null, null,
                    exception);
        } catch (InterruptedException exception) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw failure(ShopifyUcpTransportFailure.TIMEOUT, "Shopify UCP request was interrupted", null, null,
                    exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ShopifyUcpTransportException transportException) {
                throw transportException;
            }
            throw classify(cause);
        }
    }

    private UcpToolResponse callWithSingleUnauthorizedRefresh(
            RestClient restClient,
            ShopifyUcpRequestOptions options,
            String toolName,
            Object arguments
    ) {
        ShopifyBearerAuthenticationResult authentication = prepare(options);
        try {
            return callOnce(restClient, options, toolName, arguments, authentication);
        } catch (UnauthorizedResponseException rejected) {
            ShopifyBearerAuthenticationResult refreshed = refresh(authentication, options);
            try {
                return callOnce(restClient, options, toolName, arguments, refreshed);
            } catch (UnauthorizedResponseException secondRejection) {
                throw failure(
                        ShopifyUcpTransportFailure.AUTHENTICATION,
                        "Shopify rejected bearer authentication after one refresh",
                        null,
                        401,
                        secondRejection
                );
            }
        }
    }

    private ShopifyBearerAuthenticationResult prepare(ShopifyUcpRequestOptions options) {
        try {
            ShopifyBearerAuthenticationResult result = authenticationStrategy.prepare(options.requiredScopes());
            if (!result.decision().available()) {
                throw failure(
                        ShopifyUcpTransportFailure.AUTHENTICATION,
                        "Shopify bearer authentication is unavailable: " + result.decision().availability(),
                        null,
                        null,
                        null
                );
            }
            return result;
        } catch (ShopifyRateLimitException exception) {
            throw failure(ShopifyUcpTransportFailure.RATE_LIMITED, "Shopify authentication was rate limited",
                    exception.retryAfter().orElse(null), 429, exception);
        } catch (ShopifyAuthenticationException exception) {
            throw failure(ShopifyUcpTransportFailure.AUTHENTICATION, "Shopify authentication failed", null, null,
                    exception);
        } catch (ShopifyTransientException exception) {
            throw failure(ShopifyUcpTransportFailure.TRANSIENT_UPSTREAM,
                    "Shopify authentication was temporarily unavailable", null, null, exception);
        }
    }

    private ShopifyBearerAuthenticationResult refresh(
            ShopifyBearerAuthenticationResult rejected,
            ShopifyUcpRequestOptions options
    ) {
        try {
            ShopifyBearerAuthenticationResult refreshed = authenticationStrategy.refreshAfterUnauthorized(
                    rejected,
                    options.requiredScopes()
            );
            if (!refreshed.decision().available()) {
                throw failure(
                        ShopifyUcpTransportFailure.AUTHENTICATION,
                        "Shopify bearer refresh is unavailable: " + refreshed.decision().availability(),
                        null,
                        401,
                        null
                );
            }
            return refreshed;
        } catch (ShopifyRateLimitException exception) {
            throw failure(ShopifyUcpTransportFailure.RATE_LIMITED, "Shopify authentication refresh was rate limited",
                    exception.retryAfter().orElse(null), 429, exception);
        } catch (ShopifyAuthenticationException exception) {
            throw failure(ShopifyUcpTransportFailure.AUTHENTICATION, "Shopify authentication refresh failed",
                    null, 401, exception);
        } catch (ShopifyTransientException exception) {
            throw failure(ShopifyUcpTransportFailure.TRANSIENT_UPSTREAM,
                    "Shopify authentication refresh was temporarily unavailable", null, null, exception);
        }
    }

    private UcpToolResponse callOnce(
            RestClient restClient,
            ShopifyUcpRequestOptions options,
            String toolName,
            Object arguments,
            ShopifyBearerAuthenticationResult authentication
    ) {
        try {
            return ucpMcpClient.callToolAuthenticatedAllowingJsonToolErrors(
                    restClient,
                    options.endpoint(),
                    toolName,
                    arguments,
                    authentication::applyTo
            );
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401) {
                throw new UnauthorizedResponseException();
            }
            if (status == 403) {
                throw failure(ShopifyUcpTransportFailure.AUTHENTICATION,
                        "Shopify rejected bearer authorization", null, status, null);
            }
            if (status == 429) {
                throw failure(ShopifyUcpTransportFailure.RATE_LIMITED, "Shopify Global Catalog rate limited the request",
                        retryAfter(exception.getResponseHeaders()), status, null);
            }
            if (status == 408) {
                throw failure(ShopifyUcpTransportFailure.TIMEOUT, "Shopify Global Catalog timed out the request",
                        null, status, null);
            }
            if (HttpStatusCode.valueOf(status).is5xxServerError()) {
                throw failure(ShopifyUcpTransportFailure.TRANSIENT_UPSTREAM,
                        "Shopify Global Catalog returned a server failure", null, status, null);
            }
            throw failure(ShopifyUcpTransportFailure.INVALID_REQUEST,
                    "Shopify Global Catalog rejected the request", null, status, null);
        } catch (UcpMcpRemoteErrorException exception) {
            throw failure(ShopifyUcpTransportFailure.INVALID_REQUEST,
                    "Shopify Global Catalog returned a JSON-RPC error", null, null, exception);
        } catch (UcpMcpException exception) {
            throw failure(ShopifyUcpTransportFailure.MALFORMED_RESPONSE,
                    "Shopify Global Catalog returned a malformed MCP response", null, null, exception);
        } catch (RestClientException exception) {
            throw classify(exception);
        }
    }

    private ShopifyUcpTransportException classify(Throwable throwable) {
        if (hasCause(throwable, HttpMessageConversionException.class)) {
            return failure(ShopifyUcpTransportFailure.MALFORMED_RESPONSE,
                    "Shopify Global Catalog response was not valid JSON", null, null, null);
        }
        if (throwable instanceof ResourceAccessException
                && (hasCause(throwable, SocketTimeoutException.class)
                || hasCause(throwable, InterruptedIOException.class))) {
            return failure(ShopifyUcpTransportFailure.TIMEOUT, "Shopify Global Catalog request timed out",
                    null, null, throwable);
        }
        return failure(ShopifyUcpTransportFailure.TRANSIENT_UPSTREAM,
                "Could not reach Shopify Global Catalog", null, null, throwable);
    }

    private RestClient restClient(ShopifyUcpRequestOptions options) {
        if (fixedRestClient != null) {
            return fixedRestClient;
        }
        ShopifyUcpClientHttpRequestFactory requestFactory = new ShopifyUcpClientHttpRequestFactory(
                options.connectTimeout(),
                options.readTimeout()
        );
        return restClientBuilder.clone().requestFactory(requestFactory).build();
    }

    private Duration retryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration duration = Duration.between(clock.instant(), retryAt);
                return duration.isNegative() ? Duration.ZERO : duration;
            } catch (DateTimeException | ArithmeticException invalidDate) {
                return null;
            }
        }
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ShopifyUcpTransportException failure(
            ShopifyUcpTransportFailure type,
            String message,
            Duration retryAfter,
            Integer status,
            Throwable cause
    ) {
        return new ShopifyUcpTransportException(type, message, retryAfter, status, cause);
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }

    private static final class UnauthorizedResponseException extends RuntimeException {
    }
}
