package com.meant.api.provider.shopify.auth;

import com.meant.api.provider.shopify.auth.ShopifyTokenRequest;
import com.meant.api.provider.shopify.auth.ShopifyTokenResponse;
import com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties;
import java.time.Clock;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
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
                        throw new ShopifyRateLimitException(ShopifyHttpResponseSupport.retryAfter(
                                httpResponse.getHeaders(), clock));
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
            if (ShopifyHttpResponseSupport.hasCause(exception, HttpMessageConversionException.class)) {
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

}
