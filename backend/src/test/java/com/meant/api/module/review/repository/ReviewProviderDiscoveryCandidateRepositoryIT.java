package com.meant.api.module.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTest;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.review.constant.ReviewProductIdType;
import com.meant.api.module.review.constant.ReviewProviderStatus;
import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.entity.ReviewProvider;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class ReviewProviderDiscoveryCandidateRepositoryIT extends PostgresIntegrationTest {

    @Autowired
    private ReviewProviderDiscoveryCandidateRepository candidateRepository;

    @Autowired
    private ReviewProviderRepository reviewProviderRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantRawRepository merchantRawRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("truncate table review_provider, merchant, merchant_raw cascade");
    }

    @Test
    void claimCandidatesExecutesJpqlAndCreatesLeaseForMerchantWithoutProvider() {
        Instant now = Instant.parse("2026-07-03T12:00:00Z");
        Instant claimExpiresAt = Instant.parse("2026-07-03T12:05:00Z");
        Merchant merchant = saveMerchant("merchant-claim-new.example", now);

        var claimed = candidateRepository.claimCandidates(now, claimExpiresAt, 10);

        assertThat(claimed)
                .extracting(candidate -> candidate.merchantId())
                .containsExactly(merchant.getId());
        ReviewProvider provider = reviewProviderRepository.findByMerchantId(merchant.getId()).orElseThrow();
        assertThat(provider.getMerchantDomain()).isEqualTo("merchant-claim-new.example");
        assertThat(provider.getProvider()).isEqualTo(ReviewProviderType.UNKNOWN);
        assertThat(provider.getStatus()).isEqualTo(ReviewProviderStatus.FAILED_RETRYABLE);
        assertThat(provider.getProductIdType()).isEqualTo(ReviewProductIdType.UNKNOWN);
        assertThat(provider.getNextCheckAt()).isEqualTo(claimExpiresAt);
    }

    @Test
    void claimCandidatesPreservesExistingProviderConfigurationDuringLease() {
        Instant now = Instant.parse("2026-07-03T12:00:00Z");
        Instant claimExpiresAt = Instant.parse("2026-07-03T12:05:00Z");
        Merchant merchant = saveMerchant("merchant-claim-existing.example", now.minusSeconds(10));
        reviewProviderRepository.save(ReviewProvider.builder()
                .merchantId(merchant.getId())
                .merchantDomain("old.example")
                .provider(ReviewProviderType.KLAVIYO)
                .status(ReviewProviderStatus.FAILED_RETRYABLE)
                .providerKey("company-1")
                .productIdType(ReviewProductIdType.SHOPIFY_NUMERIC_ID)
                .sourceUrl("https://merchant-claim-existing.example/products/tee")
                .evidence("klaviyo")
                .nextCheckAt(now.minusSeconds(1))
                .errorMessage("Previous retryable failure")
                .createdAt(now.minusSeconds(3600))
                .updatedAt(now.minusSeconds(60))
                .build());

        candidateRepository.claimCandidates(now, claimExpiresAt, 10);

        ReviewProvider provider = reviewProviderRepository.findByMerchantId(merchant.getId()).orElseThrow();
        assertThat(provider.getMerchantDomain()).isEqualTo("merchant-claim-existing.example");
        assertThat(provider.getStatus()).isEqualTo(ReviewProviderStatus.FAILED_RETRYABLE);
        assertThat(provider.getProvider()).isEqualTo(ReviewProviderType.KLAVIYO);
        assertThat(provider.getProviderKey()).isEqualTo("company-1");
        assertThat(provider.getProductIdType()).isEqualTo(ReviewProductIdType.SHOPIFY_NUMERIC_ID);
        assertThat(provider.getSourceUrl()).isEqualTo("https://merchant-claim-existing.example/products/tee");
        assertThat(provider.getEvidence()).isEqualTo("klaviyo");
        assertThat(provider.getErrorMessage()).isNull();
        assertThat(provider.getNextCheckAt()).isEqualTo(claimExpiresAt);
    }

    private Merchant saveMerchant(String domain, Instant now) {
        UUID id = UUID.randomUUID();
        MerchantRaw merchantRaw = merchantRawRepository.save(MerchantRaw.builder()
                .id(UUID.randomUUID())
                .datasetRowIdx(Math.abs(id.hashCode()))
                .domain(domain)
                .status("OK")
                .ucpUrl("https://%s/.well-known/ucp.json".formatted(domain))
                .httpStatus(200)
                .ucpVersion("1.0")
                .hasCheckout(true)
                .hasIdentityLinking(false)
                .hasCartManagement(true)
                .hasOrder(false)
                .hasPaymentToken(false)
                .capabilityCount(1)
                .transports("[]")
                .fetchedAt(now)
                .processed(true)
                .processingStatus("SUCCESS")
                .sourceHash("raw-hash-%s".formatted(id))
                .active(true)
                .lastSeenAt(now)
                .build());
        return merchantRepository.save(Merchant.builder()
                .id(id)
                .merchantRaw(merchantRaw)
                .domain(domain)
                .ucpUrl("https://%s/.well-known/ucp.json".formatted(domain))
                .ucpVersion("1.0")
                .advertisedMcpEndpoint("https://%s/api/mcp".formatted(domain))
                .profileHash("hash-%s".formatted(id))
                .name("Merchant")
                .description("Description")
                .about("About")
                .targetAudience("Customers")
                .profileQuestion("Question")
                .profileAnswerRaw("Answer")
                .active(true)
                .lastProfiledAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }
}
