package com.meant.api.plugin.transport.client;

import com.meant.api.plugin.transport.dto.ShopifyTokenRequest;
import com.meant.api.plugin.transport.dto.ShopifyTokenResponse;
import com.meant.api.plugin.transport.profile.ShopifyAgentAuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ShopifyTokenClient {

    private static final String CLIENT_CREDENTIALS = "client_credentials";

    private final RestClient restClient;
    private final ShopifyAgentAuthProperties properties;
    private final Clock clock;

    @Autowired
    public ShopifyTokenClient(RestClient.Builder restClientBuilder, ShopifyAgentAuthProperties properties) {
        this(restClientBuilder, properties, Clock.systemUTC());
    }

    ShopifyTokenClient(
            RestClient.Builder restClientBuilder,
            ShopifyAgentAuthProperties properties,
            Clock clock
    ) {
        this.restClient = Objects.requireNonNull(restClientBuilder, "restClientBuilder").clone().build();
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ShopifyTokenResponse exchangeClientCredentials() {
        if (!properties.isEnabled()) {
            throw new ShopifyAuthenticationException("Shopify agent authentication is disabled");
        }

        ShopifyTokenRequest request = new ShopifyTokenRequest(
                properties.clientId(),
                properties.clientSecret(),
                CLIENT_CREDENTIALS
        );
        try {
            ShopifyTokenResponse response = restClient.post()
                    .uri(properties.tokenEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(this::isAuthenticationFailure, (httpRequest, httpResponse) -> {
                        throw new ShopifyAuthenticationException("Shopify rejected the configured client credentials");
                    })
                    .onStatus(status -> status.value() == 429, (httpRequest, httpResponse) -> {
                        throw new ShopifyRateLimitException(retryAfter(httpResponse.getHeaders()));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (httpRequest, httpResponse) -> {
                        throw new ShopifyTransientException("Shopify token endpoint returned a server failure");
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (httpRequest, httpResponse) -> {
                        throw new ShopifyTokenClientException("Shopify token endpoint rejected the request");
                    })
                    .body(ShopifyTokenResponse.class);
            validate(response);
            return response;
        } catch (ShopifyTokenClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            if (hasConversionCause(exception)) {
                throw new ShopifyMalformedTokenResponseException("Shopify token response was not valid JSON");
            }
            throw new ShopifyTransientException("Could not reach the Shopify token endpoint");
        }
    }

    private boolean isAuthenticationFailure(HttpStatusCode status) {
        return status.value() == 400 || status.value() == 401 || status.value() == 403;
    }

    private void validate(ShopifyTokenResponse response) {
        if (response == null || !StringUtils.hasText(response.accessToken())) {
            throw new ShopifyMalformedTokenResponseException("Shopify token response was missing access_token");
        }
        if (response.expiresIn() != null && response.expiresIn() <= 0) {
            throw new ShopifyMalformedTokenResponseException("Shopify token response contained an invalid expires_in");
        }
    }

    private boolean hasConversionCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpMessageConversionException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Duration retryAfter(HttpHeaders headers) {
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
            } catch (DateTimeParseException invalidDate) {
                return null;
            }
        }
    }
}
