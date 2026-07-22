package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.merchant.constant.MerchantIdentityNamespace;
import com.meant.api.module.merchant.constant.MerchantIdentityRole;
import com.meant.api.module.merchant.constant.MerchantRawSource;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.repository.MerchantIdentityRepository;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.MerchantIdentityResolution;
import com.meant.api.module.merchant.service.dto.ResolvedMerchantIdentityClaim;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class MerchantIdentityPersistenceServiceIT extends PostgresIntegrationTestSupport {

    private static final String SHOP_ID = "gid://shopify/shop/17756429";

    @Autowired
    private MerchantIdentityPersistenceService identityPersistenceService;

    @Autowired
    private MerchantIdentityRepository identityRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantRawRepository merchantRawRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        identityRepository.deleteAllInBatch();
        merchantRepository.deleteAllInBatch();
        merchantRawRepository.deleteAllInBatch();
    }

    @Test
    void concurrentWritersCannotPersistTwoMerchantsForTheSameIdentity() throws Exception {
        MerchantRaw firstSource = merchantRawRepository.saveAndFlush(merchantRaw("first-alias.example", 1));
        MerchantRaw secondSource = merchantRawRepository.saveAndFlush(merchantRaw("second-alias.example", 2));
        MerchantIdentityResolution firstIdentity = identity("first-alias.example");
        MerchantIdentityResolution secondIdentity = identity("second-alias.example");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Object> first = executor.submit(() -> persistConcurrently(
                    firstSource.getId(), firstIdentity, ready, start
            ));
            Future<Object> second = executor.submit(() -> persistConcurrently(
                    secondSource.getId(), secondIdentity, ready, start
            ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );

            assertThat(results).filteredOn(UUID.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(DataIntegrityViolationException.class::isInstance).hasSize(1);
        }

        assertThat(merchantRepository.findAll()).singleElement();
        assertThat(identityRepository.findAll()).singleElement().satisfies(identity -> {
            assertThat(identity.getNamespace()).isEqualTo(MerchantIdentityNamespace.SHOPIFY_SHOP);
            assertThat(identity.getNormalizedValue()).isEqualTo(SHOP_ID);
        });
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from merchant_raw where merchant_id is not null",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(distinct merchant_id) from merchant_identity",
                Integer.class
        )).isEqualTo(1);
    }

    private Object persistConcurrently(
            UUID sourceId,
            MerchantIdentityResolution identity,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        try {
            return transactionTemplate.execute(status -> {
                MerchantRaw source = merchantRawRepository.findById(sourceId).orElseThrow();
                if (identityPersistenceService.findOwner(source, identity).isPresent()) {
                    throw new AssertionError("Both writers must perform owner lookup before either claim is persisted");
                }
                ready.countDown();
                await(start);

                Merchant merchant = merchantRepository.saveAndFlush(merchant(source, identity.canonicalDomain()));
                identityPersistenceService.linkSourceAndPersistClaims(source, merchant, identity, Instant.now());
                identityRepository.flush();
                return merchant.getId();
            });
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the concurrent writer");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the concurrent writer", exception);
        }
    }

    private MerchantIdentityResolution identity(String canonicalDomain) {
        return new MerchantIdentityResolution(
                canonicalDomain,
                "Concurrent merchant",
                List.of(new ResolvedMerchantIdentityClaim(
                        MerchantIdentityNamespace.SHOPIFY_SHOP,
                        SHOP_ID,
                        MerchantIdentityRole.PROVIDER_ID
                ))
        );
    }

    private MerchantRaw merchantRaw(String domain, int datasetRowIdx) {
        Instant now = Instant.parse("2026-07-22T08:00:00Z");
        return MerchantRaw.builder()
                .source(MerchantRawSource.HUGGING_FACE)
                .datasetRowIdx(datasetRowIdx)
                .domain(domain)
                .status("verified")
                .ucpUrl("https://%s/.well-known/ucp".formatted(domain))
                .httpStatus(200)
                .ucpVersion("2026-04-08")
                .capabilityCount(1)
                .transports("[\"mcp\"]")
                .fetchedAt(now)
                .processed(false)
                .sourceHash("source-" + datasetRowIdx)
                .active(true)
                .lastSeenAt(now)
                .build();
    }

    private Merchant merchant(MerchantRaw source, String domain) {
        Instant now = Instant.now();
        return Merchant.builder()
                .merchantRaw(source)
                .domain(domain)
                .ucpUrl(source.getUcpUrl())
                .ucpVersion("2026-04-08")
                .profileHash("profile-" + domain)
                .name("Concurrent merchant")
                .description("")
                .about("")
                .targetAudience("")
                .profileQuestion("")
                .profileAnswerRaw("")
                .active(true)
                .lastProfiledAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
