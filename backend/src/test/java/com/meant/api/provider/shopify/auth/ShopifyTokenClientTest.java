package com.meant.api.provider.shopify.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.provider.shopify.auth.ShopifyTokenRequest;
import com.meant.api.provider.shopify.auth.ShopifyTokenResponse;
import com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ShopifyTokenClientTest {

    @Test
    void acquiresTokenWithTypedClientCredentialsRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.shopify.test/auth/access_token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.client_id").value("configured-client"))
                .andExpect(jsonPath("$.client_secret").value("configured-secret"))
                .andExpect(jsonPath("$.grant_type").value("client_credentials"))
                .andRespond(withSuccess("""
                        {
                          "access_token": "issued-token",
                          "token_type": "Bearer",
                          "scope": "catalog:read cart:write",
                          "expires_in": 3599
                        }
                        """, MediaType.APPLICATION_JSON));

        ShopifyTokenResponse response = new ShopifyTokenClient(builder, properties()).exchangeClientCredentials();

        assertThat(response.expiresIn()).isEqualTo(3599);
        assertThat(response.scope()).contains("catalog:read");
        server.verify();
    }

    @Test
    void classifiesRejectedCredentialsWithoutIncludingResponseBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.shopify.test/auth/access_token"))
                .andRespond(withRawStatus(401)
                        .body("{\"client_secret\":\"reflected-secret\",\"access_token\":\"reflected-token\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new ShopifyTokenClient(builder, properties()).exchangeClientCredentials())
                .isInstanceOf(ShopifyAuthenticationException.class)
                .hasMessage("Shopify rejected the configured client credentials")
                .hasMessageNotContaining("reflected-secret")
                .hasMessageNotContaining("reflected-token");
        server.verify();
    }

    @Test
    void classifiesRateLimitAndExposesOnlyRetryMetadata() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.shopify.test/auth/access_token"))
                .andRespond(withRawStatus(429).header(HttpHeaders.RETRY_AFTER, "7"));

        assertThatThrownBy(() -> new ShopifyTokenClient(builder, properties()).exchangeClientCredentials())
                .isInstanceOfSatisfying(ShopifyRateLimitException.class, exception ->
                        assertThat(exception.retryAfter()).contains(Duration.ofSeconds(7)));
        server.verify();
    }

    @Test
    void calculatesDateBasedRetryAfterFromInjectedClock() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.shopify.test/auth/access_token"))
                .andRespond(withRawStatus(429).header(HttpHeaders.RETRY_AFTER, "Fri, 10 Jul 2026 12:00:30 GMT"));
        Clock clock = Clock.fixed(Instant.parse("2026-07-10T12:00:00Z"), ZoneOffset.UTC);

        assertThatThrownBy(() -> new ShopifyTokenClient(builder, properties(), clock).exchangeClientCredentials())
                .isInstanceOfSatisfying(ShopifyRateLimitException.class, exception ->
                        assertThat(exception.retryAfter()).contains(Duration.ofSeconds(30)));
        server.verify();
    }

    @Test
    void classifiesMalformedTokenResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.shopify.test/auth/access_token"))
                .andRespond(withSuccess("{\"expires_in\":3599}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new ShopifyTokenClient(builder, properties()).exchangeClientCredentials())
                .isInstanceOf(ShopifyMalformedTokenResponseException.class)
                .hasMessage("Shopify token response was missing access_token");
        server.verify();
    }

    @Test
    void classifiesInvalidJsonAsMalformedResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.shopify.test/auth/access_token"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new ShopifyTokenClient(builder, properties()).exchangeClientCredentials())
                .isInstanceOf(ShopifyMalformedTokenResponseException.class)
                .hasMessage("Shopify token response was not valid JSON");
        server.verify();
    }

    @Test
    void classifiesServerFailureAsTransient() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.shopify.test/auth/access_token"))
                .andRespond(withRawStatus(503).body("temporarily unavailable"));

        assertThatThrownBy(() -> new ShopifyTokenClient(builder, properties()).exchangeClientCredentials())
                .isInstanceOf(ShopifyTransientException.class)
                .hasMessage("Shopify token endpoint returned a server failure");
        server.verify();
    }

    @Test
    void tokenModelsRedactSecretsFromToString() {
        ShopifyTokenRequest request = new ShopifyTokenRequest(
                "configured-client",
                "request-secret-value",
                "client_credentials"
        );
        ShopifyTokenResponse response = new ShopifyTokenResponse(
                "response-token-value",
                "Bearer",
                3599L,
                "catalog:read"
        );

        assertThat(request.toString())
                .contains("[redacted]")
                .doesNotContain("request-secret-value");
        assertThat(response.toString())
                .contains("[redacted]")
                .doesNotContain("response-token-value");
    }

    private ShopifyAgentAuthProperties properties() {
        return new ShopifyAgentAuthProperties(
                true,
                "test",
                "configured-client",
                "configured-secret",
                URI.create("https://api.shopify.test/auth/access_token"),
                Duration.ofMinutes(5),
                Duration.ofHours(1)
        );
    }
}
