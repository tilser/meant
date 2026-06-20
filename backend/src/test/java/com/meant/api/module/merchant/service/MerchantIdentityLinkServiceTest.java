package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.PostgresIntegrationTest;
import com.meant.api.module.merchant.constant.MerchantIdentityLinkStatus;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantIdentityLink;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.exception.MerchantIdentityLinkException;
import com.meant.api.module.merchant.repository.MerchantIdentityLinkRepository;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.command.CompleteMerchantIdentityAuthorizationCommand;
import com.meant.api.module.merchant.service.command.StartMerchantIdentityAuthorizationCommand;
import com.meant.api.module.merchant.service.dto.MerchantIdentityAccessTokenResult;
import com.meant.api.module.merchant.service.dto.MerchantIdentityAuthorizationResult;
import com.meant.api.module.merchant.service.dto.MerchantIdentityLinkResult;
import com.meant.api.module.merchant.service.query.GetMerchantIdentityAccessTokenQuery;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.UnorderedRequestExpectationManager;
import org.springframework.web.client.RestClient;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@Import(MerchantIdentityLinkServiceTest.MockRestClientConfiguration.class)
class MerchantIdentityLinkServiceTest extends PostgresIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000040");
    private static final String METADATA_URL = "https://merchant.example/.well-known/oauth-authorization-server";
    private static final String TOKEN_URL = "https://merchant.example/oauth/token";

    @Autowired
    private MerchantIdentityLinkService service;

    @Autowired
    private MerchantIdentityLinkRepository merchantIdentityLinkRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantRawRepository merchantRawRepository;

    @Autowired
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        deleteTestData();
        // The OAuth client builds its RestClient once at construction, so the mock request factory is bound
        // to the builder in MockRestClientConfiguration before the bean is created. Reset expectations here.
        server.reset();
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    private void deleteTestData() {
        merchantIdentityLinkRepository.deleteAllInBatch();
        merchantRepository.deleteAllInBatch();
        merchantRawRepository.deleteAllInBatch();
    }

    @Test
    void startAuthorizationRejectsMerchantsWithoutIdentityLinkingCapability() {
        Merchant merchant = saveMerchant(false);

        assertThatThrownBy(() -> service.startAuthorization(
                new StartMerchantIdentityAuthorizationCommand(USER_ID, merchant.getId())))
                .isInstanceOf(MerchantIdentityLinkException.class)
                .hasMessageContaining("does not support identity linking");
    }

    @Test
    void startAuthorizationStoresPendingStateAndReturnsPkceAuthorizationUrl() {
        Merchant merchant = saveMerchant(true);
        expectMetadata();

        MerchantIdentityAuthorizationResult result = service.startAuthorization(
                new StartMerchantIdentityAuthorizationCommand(USER_ID, merchant.getId()));

        assertThat(result.authorizationUrl())
                .startsWith("https://merchant.example/oauth/authorize?")
                .contains("response_type=code")
                .contains("client_id=meant-test")
                .contains("code_challenge_method=S256")
                .contains("scope=dev.ucp.shopping.order:read%20dev.ucp.shopping.account:manage");
        assertThat(result.state()).isNotBlank();
        MerchantIdentityLink link = merchantIdentityLinkRepository.findByUserIdAndMerchantId(USER_ID, merchant.getId())
                .orElseThrow();
        assertThat(link.getStatus()).isEqualTo(MerchantIdentityLinkStatus.PENDING);
        assertThat(link.getStateHash()).doesNotContain(result.state());
        assertThat(link.getCodeVerifierCiphertext()).startsWith("v1:");
        server.verify();
    }

    @Test
    void completeAuthorizationStoresEncryptedScopedTokens() {
        Merchant merchant = saveMerchant(true);
        expectMetadata();
        expectMetadata();
        expectTokenExchange("access-token", "refresh-token", 3600);
        MerchantIdentityAuthorizationResult authorization = service.startAuthorization(
                new StartMerchantIdentityAuthorizationCommand(USER_ID, merchant.getId()));

        MerchantIdentityLinkResult result = service.completeAuthorization(
                new CompleteMerchantIdentityAuthorizationCommand(
                        USER_ID,
                        authorization.state(),
                        "authorization-code",
                        "https://merchant.example"));

        assertThat(result.status()).isEqualTo(MerchantIdentityLinkStatus.CONNECTED);
        assertThat(result.scope()).isEqualTo("dev.ucp.shopping.order:read dev.ucp.shopping.loyalty:read");
        MerchantIdentityLink link = merchantIdentityLinkRepository.findByUserIdAndMerchantId(USER_ID, merchant.getId())
                .orElseThrow();
        assertThat(link.getAccessTokenCiphertext()).startsWith("v1:");
        assertThat(link.getAccessTokenCiphertext()).doesNotContain("access-token");
        assertThat(link.getRefreshTokenCiphertext()).doesNotContain("refresh-token");
        assertThat(link.getExpiresAt()).isAfter(Instant.now());
        server.verify();
    }

    @Test
    void accessTokenRefreshesExpiredConnectionBeforeReturningToken() {
        Merchant merchant = saveMerchant(true);
        expectMetadata();
        expectMetadata();
        expectTokenExchange("expired-access-token", "refresh-token", 1);
        expectMetadata();
        expectRefreshExchange("fresh-access-token", "fresh-refresh-token", 3600);
        MerchantIdentityAuthorizationResult authorization = service.startAuthorization(
                new StartMerchantIdentityAuthorizationCommand(USER_ID, merchant.getId()));
        service.completeAuthorization(new CompleteMerchantIdentityAuthorizationCommand(
                USER_ID,
                authorization.state(),
                "authorization-code",
                "https://merchant.example"));

        MerchantIdentityAccessTokenResult token = service.getAccessToken(
                new GetMerchantIdentityAccessTokenQuery(USER_ID, merchant.getId()));

        assertThat(token.accessToken()).isEqualTo("fresh-access-token");
        assertThat(token.tokenType()).isEqualTo("Bearer");
        server.verify();
    }

    private void expectMetadata() {
        server.expect(requestTo(METADATA_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "issuer": "https://merchant.example",
                          "authorization_endpoint": "https://merchant.example/oauth/authorize",
                          "token_endpoint": "https://merchant.example/oauth/token",
                          "revocation_endpoint": "https://merchant.example/oauth/revoke",
                          "scopes_supported": [
                            "dev.ucp.shopping.order:read",
                            "dev.ucp.shopping.account:manage",
                            "dev.ucp.shopping.loyalty:read"
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
    }

    private void expectTokenExchange(String accessToken, String refreshToken, long expiresIn) {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth()))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("code=authorization-code")))
                .andExpect(content().string(containsString("code_verifier=")))
                .andRespond(withSuccess(tokenJson(accessToken, refreshToken, expiresIn), MediaType.APPLICATION_JSON));
    }

    private void expectRefreshExchange(String accessToken, String refreshToken, long expiresIn) {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth()))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andExpect(content().string(containsString("refresh_token=refresh-token")))
                .andRespond(withSuccess(tokenJson(accessToken, refreshToken, expiresIn), MediaType.APPLICATION_JSON));
    }

    private String tokenJson(String accessToken, String refreshToken, long expiresIn) {
        return """
                {
                  "access_token": "%s",
                  "refresh_token": "%s",
                  "token_type": "Bearer",
                  "expires_in": %d,
                  "scope": "dev.ucp.shopping.order:read dev.ucp.shopping.loyalty:read"
                }
                """.formatted(accessToken, refreshToken, expiresIn);
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder()
                .encodeToString("meant-test:test-secret".getBytes(StandardCharsets.UTF_8));
    }

    @TestConfiguration
    static class MockRestClientConfiguration {

        // Bind the mock server to the builder up front so the OAuth client's constructor-built RestClient
        // captures the mock request factory. Unordered so the metadata/token calls can arrive in any order.
        private final MockRestServiceServer.MockRestServiceServerBuilder serverBuilder;
        private final RestClient.Builder restClientBuilder = RestClient.builder();
        private final MockRestServiceServer server;

        MockRestClientConfiguration() {
            this.serverBuilder = MockRestServiceServer.bindTo(restClientBuilder);
            this.server = serverBuilder.build(new UnorderedRequestExpectationManager());
        }

        @Bean
        @Primary
        RestClient.Builder mockRestClientBuilder() {
            return restClientBuilder;
        }

        @Bean
        MockRestServiceServer mockRestServiceServer() {
            return server;
        }
    }

    private Merchant saveMerchant(boolean hasIdentityLinking) {
        MerchantRaw raw = merchantRawRepository.save(MerchantRaw.builder()
                .datasetRowIdx(1)
                .domain("merchant.example")
                .status("verified")
                .ucpUrl("https://merchant.example/.well-known/ucp")
                .httpStatus(200)
                .ucpVersion("2026-01-23")
                .hasCheckout(true)
                .hasIdentityLinking(hasIdentityLinking)
                .hasCartManagement(true)
                .hasOrder(true)
                .hasPaymentToken(true)
                .capabilityCount(4)
                .aiBotPolicies("{}")
                .transports("[\"mcp\"]")
                .lastCheckedAt(Instant.parse("2026-04-02T09:00:15Z"))
                .lastSuccessAt(Instant.parse("2026-04-02T09:00:15Z"))
                .fetchedAt(Instant.parse("2026-04-02T09:00:15Z"))
                .processed(true)
                .sourceHash("source-hash")
                .active(true)
                .lastSeenAt(Instant.parse("2026-04-02T09:00:15Z"))
                .build());
        return merchantRepository.save(Merchant.builder()
                .merchantRaw(raw)
                .domain(raw.getDomain())
                .ucpUrl(raw.getUcpUrl())
                .ucpVersion(raw.getUcpVersion())
                .advertisedMcpEndpoint("https://merchant.example/api/mcp")
                .profileMcpEndpoint("https://merchant.example/api/mcp")
                .profileHash("profile-hash")
                .name("Merchant")
                .description("Merchant description")
                .about("About merchant")
                .targetAudience("Everyone")
                .profileQuestion("Question")
                .profileAnswerRaw("Answer")
                .active(true)
                .lastProfiledAt(Instant.parse("2026-04-02T09:00:15Z"))
                .createdAt(Instant.parse("2026-04-02T09:00:15Z"))
                .updatedAt(Instant.parse("2026-04-02T09:00:15Z"))
                .build());
    }
}
