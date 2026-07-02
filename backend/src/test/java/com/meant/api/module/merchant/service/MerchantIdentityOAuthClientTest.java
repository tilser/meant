package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.exception.MerchantIdentityLinkException;
import com.meant.api.module.merchant.properties.MerchantIdentityLinkingProperties;
import com.meant.api.module.merchant.service.dto.MerchantIdentityAuthorizationServerMetadata;
import com.meant.api.module.merchant.service.dto.MerchantIdentityTokenResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MerchantIdentityOAuthClientTest {

    private static final String METADATA_URL = "https://merchant.example/.well-known/oauth-authorization-server";
    private static final String OIDC_METADATA_URL = "https://merchant.example/.well-known/openid-configuration";
    private static final String TOKEN_URL = "https://merchant.example/oauth/token";

    @Test
    void discoversPrimaryMetadataAndUsesClientSecretBasicForTokenExchange() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MerchantIdentityOAuthClient client = client(builder, "test-secret");
        server.expect(requestTo(METADATA_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(metadataJson(
                        "https://merchant.example",
                        "[\"client_secret_basic\", \"none\"]"
                ), MediaType.APPLICATION_JSON));
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth()))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("code=authorization-code")))
                .andExpect(content().string(containsString("code_verifier=verifier")))
                .andRespond(withSuccess(tokenJson(), MediaType.APPLICATION_JSON));

        MerchantIdentityAuthorizationServerMetadata metadata = client.discover(
                "https://merchant.example/.well-known/ucp");

        assertThat(metadata.authorizationEndpoint()).isEqualTo("https://merchant.example/oauth/authorize");
        MerchantIdentityTokenResponse response = client.exchangeAuthorizationCode(
                metadata,
                "authorization-code",
                "verifier");

        assertThat(response.accessToken()).isEqualTo("access-token");
        server.verify();
    }

    @Test
    void fallsBackToOidcMetadataOnlyAfterPrimaryNotFound() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MerchantIdentityOAuthClient client = client(builder, "test-secret");
        server.expect(requestTo(METADATA_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(OIDC_METADATA_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(metadataJson(
                        "https://merchant.example",
                        "[\"client_secret_basic\"]"
                ), MediaType.APPLICATION_JSON));

        MerchantIdentityAuthorizationServerMetadata metadata = client.discover(
                "https://merchant.example/.well-known/ucp");

        assertThat(metadata.tokenEndpoint()).isEqualTo(TOKEN_URL);
        server.verify();
    }

    @Test
    void rejectsMetadataWhoseIssuerDoesNotExactlyMatchDiscoveryBase() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MerchantIdentityOAuthClient client = client(builder, "test-secret");
        server.expect(requestTo(METADATA_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(metadataJson(
                        "https://merchant.example/",
                        "[\"client_secret_basic\"]"
                ), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.discover("https://merchant.example/.well-known/ucp"))
                .isInstanceOf(MerchantIdentityLinkException.class)
                .hasMessageContaining("issuer does not match discovery base");
        server.verify();
    }

    @Test
    void publicClientUsesNoneAuthAndSendsClientIdInForm() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MerchantIdentityOAuthClient client = client(builder, "");
        server.expect(requestTo(METADATA_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(metadataJson(
                        "https://merchant.example",
                        "[\"none\"]"
                ), MediaType.APPLICATION_JSON));
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("client_id=meant-test")))
                .andExpect(content().string(containsString("code_verifier=verifier")))
                .andRespond(withSuccess(tokenJson(), MediaType.APPLICATION_JSON));

        MerchantIdentityAuthorizationServerMetadata metadata = client.discover(
                "https://merchant.example/.well-known/ucp");
        MerchantIdentityTokenResponse response = client.exchangeAuthorizationCode(
                metadata,
                "authorization-code",
                "verifier");

        assertThat(response.tokenType()).isEqualTo("Bearer");
        server.verify();
    }

    private MerchantIdentityOAuthClient client(RestClient.Builder builder, String clientSecret) {
        return new MerchantIdentityOAuthClient(
                builder,
                new MerchantIdentityLinkingProperties(
                        "meant-test",
                        clientSecret,
                        URI.create("http://localhost:3000/oauth/merchant-callback"),
                        "test-merchant-identity-link-token-secret",
                        List.of("dev.ucp.shopping.order:read"),
                        Duration.ofMinutes(5)
                )
        );
    }

    private String metadataJson(String issuer, String authenticationMethods) {
        return """
                {
                  "issuer": "%s",
                  "authorization_endpoint": "https://merchant.example/oauth/authorize",
                  "token_endpoint": "https://merchant.example/oauth/token",
                  "revocation_endpoint": "https://merchant.example/oauth/revoke",
                  "scopes_supported": [
                    "dev.ucp.shopping.order:read"
                  ],
                  "code_challenge_methods_supported": ["S256"],
                  "token_endpoint_auth_methods_supported": %s,
                  "authorization_response_iss_parameter_supported": true
                }
                """.formatted(issuer, authenticationMethods);
    }

    private String tokenJson() {
        return """
                {
                  "access_token": "access-token",
                  "refresh_token": "refresh-token",
                  "token_type": "Bearer",
                  "expires_in": 3600,
                  "scope": "dev.ucp.shopping.order:read"
                }
                """;
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder()
                .encodeToString("meant-test:test-secret".getBytes(StandardCharsets.UTF_8));
    }
}
