package com.meant.api.provider.shopify.auth;

import com.meant.api.plugin.transport.client.UcpMcpClient;
import com.meant.api.plugin.transport.client.UcpMcpException;
import com.meant.api.plugin.transport.client.UcpMcpRemoteErrorException;

import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.provider.shopify.auth.ShopifyUcpRequestOptions;
import jakarta.annotation.PreDestroy;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
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
    private final ConcurrentMap<ClientTimeouts, RestClient> restClients = new ConcurrentHashMap<>();

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
        return authenticate(
                () -> authenticationStrategy.prepare(options.requiredScopes()),
                "authentication",
                null
        );
    }

    private ShopifyBearerAuthenticationResult refresh(
            ShopifyBearerAuthenticationResult rejected,
            ShopifyUcpRequestOptions options
    ) {
        return authenticate(
                () -> authenticationStrategy.refreshAfterUnauthorized(rejected, options.requiredScopes()),
                "authentication refresh",
                401
        );
    }

    private ShopifyBearerAuthenticationResult authenticate(
            Supplier<ShopifyBearerAuthenticationResult> authentication,
            String phase,
            Integer upstreamStatus
    ) {
        try {
            ShopifyBearerAuthenticationResult result = authentication.get();
            if (!result.decision().available()) {
                throw failure(
                        ShopifyUcpTransportFailure.AUTHENTICATION,
                        "Shopify bearer " + phase + " is unavailable: " + result.decision().availability(),
                        null,
                        upstreamStatus,
                        null
                );
            }
            return result;
        } catch (ShopifyRateLimitException exception) {
            throw failure(ShopifyUcpTransportFailure.RATE_LIMITED, "Shopify " + phase + " was rate limited",
                    exception.retryAfter().orElse(null), 429, exception);
        } catch (ShopifyAuthenticationException exception) {
            throw failure(ShopifyUcpTransportFailure.AUTHENTICATION, "Shopify " + phase + " failed",
                    null, upstreamStatus, exception);
        } catch (ShopifyTransientException exception) {
            throw failure(ShopifyUcpTransportFailure.TRANSIENT_UPSTREAM,
                    "Shopify " + phase + " was temporarily unavailable", null, upstreamStatus, exception);
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
                throw failure(ShopifyUcpTransportFailure.RATE_LIMITED, "Shopify UCP endpoint rate limited the request",
                        ShopifyHttpResponseSupport.retryAfter(exception.getResponseHeaders(), clock), status, null);
            }
            if (status == 408) {
                throw failure(ShopifyUcpTransportFailure.TIMEOUT, "Shopify UCP endpoint timed out the request",
                        null, status, null);
            }
            if (HttpStatusCode.valueOf(status).is5xxServerError()) {
                throw failure(ShopifyUcpTransportFailure.TRANSIENT_UPSTREAM,
                        "Shopify UCP endpoint returned a server failure", null, status, null);
            }
            throw failure(ShopifyUcpTransportFailure.INVALID_REQUEST,
                    "Shopify UCP endpoint rejected the request", null, status, null);
        } catch (UcpMcpRemoteErrorException exception) {
            throw failure(ShopifyUcpTransportFailure.INVALID_REQUEST,
                    "Shopify UCP endpoint returned a JSON-RPC error", null, null, exception);
        } catch (UcpMcpException exception) {
            throw failure(ShopifyUcpTransportFailure.MALFORMED_RESPONSE,
                    "Shopify UCP endpoint returned a malformed MCP response", null, null, exception);
        } catch (RestClientException exception) {
            throw classify(exception);
        }
    }

    private ShopifyUcpTransportException classify(Throwable throwable) {
        if (ShopifyHttpResponseSupport.hasCause(throwable, HttpMessageConversionException.class)) {
            return failure(ShopifyUcpTransportFailure.MALFORMED_RESPONSE,
                    "Shopify UCP endpoint response was not valid JSON", null, null, null);
        }
        if (throwable instanceof ResourceAccessException
                && (ShopifyHttpResponseSupport.hasCause(throwable, SocketTimeoutException.class)
                || ShopifyHttpResponseSupport.hasCause(throwable, InterruptedIOException.class))) {
            return failure(ShopifyUcpTransportFailure.TIMEOUT, "Shopify UCP request timed out",
                    null, null, throwable);
        }
        return failure(ShopifyUcpTransportFailure.TRANSIENT_UPSTREAM,
                "Could not reach Shopify UCP endpoint", null, null, throwable);
    }

    private RestClient restClient(ShopifyUcpRequestOptions options) {
        if (fixedRestClient != null) {
            return fixedRestClient;
        }
        return restClients.computeIfAbsent(
                new ClientTimeouts(options.connectTimeout(), options.readTimeout()),
                timeouts -> restClientBuilder.clone()
                        .requestFactory(new ShopifyUcpClientHttpRequestFactory(
                                timeouts.connectTimeout(),
                                timeouts.readTimeout()
                        ))
                        .build()
        );
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

    private record ClientTimeouts(Duration connectTimeout, Duration readTimeout) {
    }
}
