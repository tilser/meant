package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTest;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantCapability;
import com.meant.api.module.merchant.entity.MerchantCapabilityExtension;
import com.meant.api.module.merchant.entity.MerchantCategory;
import com.meant.api.module.merchant.entity.MerchantMcpToolsList;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.repository.MerchantCapabilityExtensionRepository;
import com.meant.api.module.merchant.repository.MerchantCapabilityRepository;
import com.meant.api.module.merchant.repository.MerchantCapabilityRequirementRepository;
import com.meant.api.module.merchant.repository.MerchantCategoryRepository;
import com.meant.api.module.merchant.repository.MerchantMcpToolsListRepository;
import com.meant.api.module.merchant.repository.MerchantPaymentHandlerRepository;
import com.meant.api.module.merchant.repository.MerchantPopularSearchRepository;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.repository.MerchantServiceRepository;
import com.meant.api.module.merchant.service.command.EnrichMerchantsCommand;
import com.meant.api.module.merchant.service.dto.MerchantMcpProfileResult;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolsListFetchResult;
import com.meant.api.module.merchant.service.dto.StorePolicyFaqEntry;
import com.meant.api.module.merchant.service.dto.UcpCapabilityDefinition;
import com.meant.api.module.merchant.service.dto.UcpCapabilityRequires;
import com.meant.api.module.merchant.service.dto.UcpPaymentHandlerDefinition;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.dto.UcpProfileFetchResult;
import com.meant.api.module.merchant.service.dto.UcpResourceReference;
import com.meant.api.module.merchant.service.dto.UcpServiceDefinition;
import com.meant.api.module.merchant.service.dto.UcpVersionRange;
import com.meant.api.plugin.transport.profile.AgentProfileHashProvider;
import jakarta.validation.ConstraintViolationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class MerchantEnrichmentServiceTest extends PostgresIntegrationTest {

    @Autowired
    private MerchantEnrichmentService service;

    @Autowired
    private MerchantRawRepository merchantRawRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantServiceRepository merchantServiceRepository;

    @Autowired
    private MerchantCapabilityRepository merchantCapabilityRepository;

    @Autowired
    private MerchantCapabilityExtensionRepository merchantCapabilityExtensionRepository;

    @Autowired
    private MerchantCapabilityRequirementRepository merchantCapabilityRequirementRepository;

    @Autowired
    private MerchantMcpToolsListRepository merchantMcpToolsListRepository;

    @Autowired
    private MerchantPaymentHandlerRepository merchantPaymentHandlerRepository;

    @Autowired
    private MerchantCategoryRepository merchantCategoryRepository;

    @Autowired
    private MerchantPopularSearchRepository merchantPopularSearchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeUcpProfileClient ucpProfileClient;

    @Autowired
    private FakeMerchantDomainMcpClient merchantDomainMcpClient;

    @Autowired
    private FakeMerchantMcpToolClient merchantMcpToolClient;

    @BeforeEach
    void setUp() {
        merchantCapabilityExtensionRepository.deleteAllInBatch();
        merchantCapabilityRequirementRepository.deleteAllInBatch();
        merchantCapabilityRepository.deleteAllInBatch();
        merchantServiceRepository.deleteAllInBatch();
        merchantMcpToolsListRepository.deleteAllInBatch();
        merchantPaymentHandlerRepository.deleteAllInBatch();
        merchantCategoryRepository.deleteAllInBatch();
        merchantPopularSearchRepository.deleteAllInBatch();
        merchantRepository.deleteAllInBatch();
        merchantRawRepository.deleteAllInBatch();
        ucpProfileClient.profile = ucpProfile();
        ucpProfileClient.rawProfile = rawProfile("gold");
        ucpProfileClient.endpoint = "https://allbirds.com/.well-known/ucp.json";
        ucpProfileClient.capturedAt = Instant.parse("2026-06-30T08:15:30Z");
        merchantDomainMcpClient.profile = mcpProfile();
        merchantMcpToolClient.toolsList = new MerchantMcpToolsListFetchResult(
                "https://allbirds.com/api/mcp",
                "{\"tools\":[{\"name\":\"search_catalog\"}]}"
        );
    }

    @Test
    void enrichMerchantsSavesMerchantAndNormalizedChildRows() {
        merchantRawRepository.save(merchantRaw("allbirds.com"));

        service.enrichMerchants(new EnrichMerchantsCommand(10));

        Merchant merchant = merchantRepository.findByDomain("allbirds.com").orElseThrow();
        assertThat(merchant.getAdvertisedMcpEndpoint()).isEqualTo("https://shop.example/api/ucp/mcp");
        assertThat(merchant.getProfileMcpEndpoint()).isEqualTo("https://allbirds.com/api/mcp");
        assertThat(merchant.getUcpVersion()).isEqualTo("2026-01-23");
        assertThat(merchant.getDescription()).isEqualTo("Comfortable shoes and apparel.");
        assertThat(merchant.getProfileRaw()).contains("\"x-merchant-extension\"");
        assertThat(merchant.getProfileCapturedAt()).isEqualTo(Instant.parse("2026-06-30T08:15:30Z"));
        assertThat(merchant.getProfileEndpoint()).isEqualTo("https://allbirds.com/.well-known/ucp.json");
        assertThat(merchant.getProfileProtocolVersion()).isEqualTo("2026-01-23");
        assertThat(merchant.getProfileAgentProfileHash()).isEqualTo("agent-profile-test-hash");
        assertThat(merchant.isActive()).isTrue();
        assertThat(merchantServiceRepository.count()).isEqualTo(2);
        assertThat(merchantCapabilityRepository.count()).isEqualTo(4);
        assertThat(merchantCapabilityExtensionRepository.count()).isEqualTo(4);
        assertThat(merchantCapabilityRequirementRepository.count()).isEqualTo(1);
        MerchantCapability checkoutCapability = merchantCapabilityRepository.findAll().stream()
                .filter(capability -> capability.getName().equals("dev.ucp.shopping.checkout"))
                .findFirst()
                .orElseThrow();
        assertThat(merchantCapabilityExtensionRepository.findByMerchantCapabilityId(checkoutCapability.getId()).stream()
                .map(MerchantCapabilityExtension::getExtensionCapabilityName))
                .containsExactlyInAnyOrder("dev.ucp.shopping.fulfillment", "dev.ucp.shopping.discount");
        assertThat(merchantPaymentHandlerRepository.count()).isEqualTo(1);
        assertThat(merchantCategoryRepository.findByMerchant(merchant))
                .extracting(MerchantCategory::getNormalizedName)
                .containsExactlyInAnyOrder("shoes", "apparel");
        assertThat(merchantPopularSearchRepository.findByMerchant(merchant))
                .extracting("searchText")
                .containsExactlyInAnyOrder("wool runners", "sneakers");
        assertThat(jdbcTemplate.queryForObject(
                "select profile_raw -> 'x-merchant-extension' ->> 'tier' from merchant where domain = ?",
                String.class,
                "allbirds.com"
        )).isEqualTo("gold");
        MerchantMcpToolsList toolsList = merchantMcpToolsListRepository
                .findByMerchantIdAndAgentProfileHash(merchant.getId(), "agent-profile-test-hash")
                .orElseThrow();
        assertThat(toolsList.getEndpoint()).isEqualTo("https://allbirds.com/api/mcp");
        assertThat(toolsList.getToolsListRaw()).contains("search_catalog");
        assertThat(toolsList.getCapturedAt()).isNotNull();
        assertThat(merchant.getProfileToolsListHash()).isEqualTo(toolsList.getToolsListHash());

        MerchantRaw raw = merchantRawRepository.findByDomain("allbirds.com").orElseThrow();
        assertThat(raw.isProcessed()).isTrue();
        assertThat(raw.getProcessingStatus()).isEqualTo("PROCESSED_UPDATED");
        assertThat(raw.getProcessingError()).isNull();
    }

    @Test
    void enrichMerchantsSkipsChildReplacementWhenProfileHashIsUnchanged() {
        merchantRawRepository.save(merchantRaw("allbirds.com"));
        service.enrichMerchants(new EnrichMerchantsCommand(10));
        Merchant merchant = merchantRepository.findByDomain("allbirds.com").orElseThrow();
        UUID categoryId = merchantCategoryRepository.findByMerchant(merchant).getFirst().getId();
        MerchantRaw raw = merchantRawRepository.findByDomain("allbirds.com").orElseThrow();
        raw.updateFromImport(
                1,
                "verified",
                "https://www.allbirds.com/.well-known/ucp",
                200,
                "2026-01-23",
                true,
                false,
                true,
                true,
                true,
                4,
                "{}",
                "[\"mcp\"]",
                Instant.parse("2026-04-02T09:00:15Z"),
                Instant.parse("2026-04-02T09:00:15Z"),
                Instant.parse("2026-04-04T09:00:15Z"),
                "changed-source-hash"
        );
        merchantRawRepository.save(raw);
        merchantDomainMcpClient.profile = new MerchantMcpProfileResult("https://www.allbirds.com/api/mcp", mcpProfile().entry());
        ucpProfileClient.rawProfile = rawProfile("platinum");
        merchantMcpToolClient.toolsList = new MerchantMcpToolsListFetchResult(
                "https://www.allbirds.com/api/mcp",
                "{\"tools\":[{\"name\":\"lookup_catalog\"}]}"
        );

        service.enrichMerchants(new EnrichMerchantsCommand(10));

        MerchantRaw processedRaw = merchantRawRepository.findByDomain("allbirds.com").orElseThrow();
        Merchant updatedMerchant = merchantRepository.findByDomain("allbirds.com").orElseThrow();
        assertThat(processedRaw.isProcessed()).isTrue();
        assertThat(processedRaw.getProcessingStatus()).isEqualTo("PROCESSED_UNCHANGED");
        assertThat(updatedMerchant.getUcpUrl()).isEqualTo("https://www.allbirds.com/.well-known/ucp");
        assertThat(updatedMerchant.getProfileMcpEndpoint()).isEqualTo("https://www.allbirds.com/api/mcp");
        assertThat(merchantCategoryRepository.findByMerchant(updatedMerchant).getFirst().getId()).isEqualTo(categoryId);
        assertThat(jdbcTemplate.queryForObject(
                "select profile_raw -> 'x-merchant-extension' ->> 'tier' from merchant where domain = ?",
                String.class,
                "allbirds.com"
        )).isEqualTo("platinum");
        assertThat(merchantMcpToolsListRepository
                .findByMerchantIdAndAgentProfileHash(updatedMerchant.getId(), "agent-profile-test-hash"))
                .hasValueSatisfying(toolsList -> {
                    assertThat(toolsList.getEndpoint()).isEqualTo("https://www.allbirds.com/api/mcp");
                    assertThat(toolsList.getToolsListRaw()).contains("lookup_catalog");
                    assertThat(updatedMerchant.getProfileToolsListHash()).isEqualTo(toolsList.getToolsListHash());
                });
    }

    @Test
    void enrichMerchantsBacksOffRecentRetryableFailures() {
        Instant now = Instant.now();
        MerchantRaw newMerchant = merchantRaw("new.example");
        MerchantRaw recentFailure = merchantRaw("recent-failure.example");
        recentFailure.markProcessingFailure(
                "FAILED_RETRYABLE",
                "merchant returned 429",
                now.minus(Duration.ofHours(1))
        );
        MerchantRaw staleFailure = merchantRaw("stale-failure.example");
        staleFailure.markProcessingFailure(
                "FAILED_RETRYABLE",
                "merchant returned 429",
                now.minus(Duration.ofDays(2))
        );
        merchantRawRepository.saveAll(List.of(newMerchant, recentFailure, staleFailure));

        service.enrichMerchants(new EnrichMerchantsCommand(10));

        assertThat(merchantRawRepository.findByDomain("new.example").orElseThrow().isProcessed()).isTrue();
        assertThat(merchantRawRepository.findByDomain("stale-failure.example").orElseThrow().isProcessed()).isTrue();
        MerchantRaw skippedFailure = merchantRawRepository.findByDomain("recent-failure.example").orElseThrow();
        assertThat(skippedFailure.isProcessed()).isFalse();
        assertThat(skippedFailure.getProcessingStatus()).isEqualTo("FAILED_RETRYABLE");
        assertThat(skippedFailure.getProcessingError()).isEqualTo("merchant returned 429");
    }

    @Test
    void enrichMerchantsValidatesNullCommandAtServiceBoundary() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.enrichMerchants(null))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private MerchantRaw merchantRaw(String domain) {
        return MerchantRaw.builder()
                .datasetRowIdx(1)
                .domain(domain)
                .status("verified")
                .ucpUrl("https://%s/.well-known/ucp".formatted(domain))
                .httpStatus(200)
                .ucpVersion("2026-01-23")
                .hasCheckout(true)
                .hasIdentityLinking(false)
                .hasCartManagement(true)
                .hasOrder(true)
                .hasPaymentToken(true)
                .capabilityCount(4)
                .aiBotPolicies("{}")
                .transports("[\"mcp\"]")
                .lastCheckedAt(Instant.parse("2026-04-02T09:00:15Z"))
                .lastSuccessAt(Instant.parse("2026-04-02T09:00:15Z"))
                .fetchedAt(Instant.parse("2026-04-02T09:00:15Z"))
                .processed(false)
                .sourceHash("source-hash")
                .active(true)
                .lastSeenAt(Instant.parse("2026-04-02T09:00:15Z"))
                .build();
    }

    private UcpProfile ucpProfile() {
        return new UcpProfile(
                "2026-01-23",
                Map.of("2026-01-23", "https://ucp.dev/2026-01-23"),
                Map.of("dev.ucp.shopping", List.of(
                        new UcpServiceDefinition(
                                "dev.ucp.shopping",
                                "1.0.0",
                                new UcpResourceReference("https://ucp.dev/spec"),
                                "mcp",
                                "https://shop.example/api/ucp/mcp",
                                new UcpResourceReference("https://ucp.dev/schema")
                        ),
                        new UcpServiceDefinition(
                                "dev.ucp.shopping",
                                "1.0.0",
                                new UcpResourceReference("https://ucp.dev/spec"),
                                "embedded",
                                "https://shop.example/api/ucp/embedded",
                                new UcpResourceReference("https://ucp.dev/schema")
                        )
                )),
                Map.of(
                        "dev.ucp.shopping.cart", List.of(
                                new UcpCapabilityDefinition(
                                        "cart",
                                        "1.0.0",
                                        new UcpResourceReference("https://ucp.dev/cart"),
                                        new UcpResourceReference("https://ucp.dev/cart-schema"),
                                        List.of(),
                                        null,
                                        null
                                )
                        ),
                        "dev.ucp.shopping.checkout", List.of(
                                new UcpCapabilityDefinition(
                                        "checkout",
                                        "1.0.0",
                                        new UcpResourceReference("https://ucp.dev/checkout"),
                                        new UcpResourceReference("https://ucp.dev/checkout-schema"),
                                        List.of(),
                                        new UcpCapabilityRequires(
                                                new UcpVersionRange("2026-01-01", "2026-12-31"),
                                                Map.of("dev.ucp.shopping.cart", new UcpVersionRange("1.0.0", "2.0.0"))
                                        ),
                                        null
                                )
                        ),
                        "dev.ucp.shopping.fulfillment", List.of(
                                new UcpCapabilityDefinition(
                                        "fulfillment",
                                        "1.0.0",
                                        new UcpResourceReference("https://ucp.dev/fulfillment"),
                                        new UcpResourceReference("https://ucp.dev/fulfillment-schema"),
                                        List.of("dev.ucp.shopping.checkout", "dev.ucp.shopping.cart"),
                                        null,
                                        null
                                )
                        ),
                        "dev.ucp.shopping.discount", List.of(
                                new UcpCapabilityDefinition(
                                        "discount",
                                        "1.0.0",
                                        new UcpResourceReference("https://ucp.dev/discount"),
                                        new UcpResourceReference("https://ucp.dev/discount-schema"),
                                        List.of("dev.ucp.shopping.checkout", "dev.ucp.shopping.cart"),
                                        null,
                                        null
                                )
                        )
                ),
                Map.of("com.google.pay", List.of(
                        new UcpPaymentHandlerDefinition(
                                "google-pay",
                                "1.0.0",
                                new UcpResourceReference("https://pay.example/spec"),
                                new UcpResourceReference("https://pay.example/schema")
                        )
                ))
        );
    }

    private MerchantMcpProfileResult mcpProfile() {
        return new MerchantMcpProfileResult(
                "https://allbirds.com/api/mcp",
                new StorePolicyFaqEntry(
                        "Tell me about your store?",
                        """
                                Description: Comfortable shoes and apparel.
                                About us: We make simple products from natural materials.
                                Target audience: Everyday shoppers.
                                Categories: Shoes, Apparel
                                Popular searches: wool runners, sneakers
                                """
                )
        );
    }

    private String rawProfile(String extensionTier) {
        return """
                {
                  "version": "2026-01-23",
                  "supported_versions": {
                    "2026-01-23": "https://ucp.dev/2026-01-23"
                  },
                  "services": {
                    "dev.ucp.shopping": [
                      {
                        "id": "dev.ucp.shopping",
                        "version": "1.0.0",
                        "transport": "mcp",
                        "endpoint": "https://shop.example/api/ucp/mcp"
                      }
                    ]
                  },
                  "x-merchant-extension": {
                    "tier": "%s"
                  }
                }
                """.formatted(extensionTier);
    }

    @TestConfiguration
    static class Configuration {

        @Bean
        @Primary
        FakeUcpProfileClient fakeUcpProfileClient() {
            return new FakeUcpProfileClient();
        }

        @Bean
        @Primary
        FakeMerchantDomainMcpClient fakeMerchantDomainMcpClient() {
            return new FakeMerchantDomainMcpClient();
        }

        @Bean
        @Primary
        FakeMerchantMcpToolClient fakeMerchantMcpToolClient() {
            return new FakeMerchantMcpToolClient();
        }

        @Bean
        @Primary
        AgentProfileHashProvider fakeAgentProfileHashProvider() {
            return () -> "agent-profile-test-hash";
        }

    }

    static class FakeUcpProfileClient extends UcpProfileClient {

        private UcpProfile profile;
        private String rawProfile;
        private String endpoint;
        private Instant capturedAt;

        FakeUcpProfileClient() {
            super(RestClient.builder(), new ObjectMapper());
        }

        @Override
        public UcpProfile fetchProfile(String merchantDomain, String ucpUrl) {
            return profile;
        }

        @Override
        public UcpProfileFetchResult fetchProfileResult(String merchantDomain, String ucpUrl) {
            return new UcpProfileFetchResult(profile, rawProfile, endpoint, capturedAt);
        }
    }

    static class FakeMerchantDomainMcpClient extends MerchantDomainMcpClient {

        private MerchantMcpProfileResult profile;

        FakeMerchantDomainMcpClient() {
            super(RestClient.builder(), new ObjectMapper());
        }

        @Override
        public MerchantMcpProfileResult fetchStoreProfile(String domain) {
            return profile;
        }
    }

    static class FakeMerchantMcpToolClient extends MerchantMcpToolClient {

        private MerchantMcpToolsListFetchResult toolsList;

        FakeMerchantMcpToolClient() {
            super(RestClient.builder().build());
        }

        @Override
        public MerchantMcpToolsListFetchResult listTools(
                String domain,
                String advertisedMcpEndpoint,
                String profileMcpEndpoint
        ) {
            return toolsList;
        }
    }
}
