package com.meant.api.module.discount.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.discount.constant.DiscountCodeSearchStatus;
import com.meant.api.module.discount.constant.DiscountCodeStatus;
import com.meant.api.module.discount.properties.DiscountCodeSearchProperties;
import com.meant.api.module.discount.service.command.SearchDiscountCodesCommand;
import com.meant.api.module.discount.service.dto.CachedDiscountCodeCandidate;
import com.meant.api.module.discount.service.dto.DiscountCodeCacheResult;
import com.meant.api.module.discount.service.dto.DiscountCodeCandidateEvaluation;
import com.meant.api.module.discount.service.dto.DiscountCodeCandidateSource;
import com.meant.api.module.discount.service.dto.DiscountCodeResult;
import com.meant.api.module.discount.service.dto.DiscountMerchant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscountCodeSearchServiceTest {

    @Test
    void freshValidCandidateReturnsCachedResultWithoutOpenRouter() {
        UUID merchantId = UUID.randomUUID();
        DiscountMerchant merchant = merchant(merchantId);
        DiscountCodeResult code = new DiscountCodeResult(
                "SAVE10",
                "10% off",
                "10% off",
                "https://merchant.example/codes",
                0.9,
                "",
                null,
                Instant.parse("2026-07-05T10:00:00Z"),
                "Discount code accepted by merchant."
        );
        FakeMerchantLookupService merchantLookupService = new FakeMerchantLookupService(merchant);
        FakePersistenceService persistenceService = new FakePersistenceService(new DiscountCodeCacheResult(
                Instant.parse("2026-07-03T10:00:00Z"),
                Instant.parse("2026-07-05T10:00:00Z"),
                List.of(code)
        ), null);
        FakeWebSearchService webSearchService = new FakeWebSearchService();
        FakeValidationService validationService = new FakeValidationService();
        DiscountCodeSearchService service = new DiscountCodeSearchService(
                merchantLookupService,
                persistenceService,
                webSearchService,
                validationService,
                properties()
        );

        var result = service.search(command(merchantId));

        assertThat(result.cached()).isTrue();
        assertThat(result.codes()).containsExactly(code);
        assertThat(webSearchService.called).isFalse();
        assertThat(validationService.called).isFalse();
        assertThat(persistenceService.saveCalled).isFalse();
    }

    @Test
    void freshEmptySearchReturnsCachedEmptyResultWithoutOpenRouter() {
        UUID merchantId = UUID.randomUUID();
        DiscountMerchant merchant = merchant(merchantId);
        DiscountCodeCacheResult emptySearchCache = new DiscountCodeCacheResult(
                Instant.parse("2026-07-03T10:00:00Z"),
                Instant.parse("2026-07-03T11:00:00Z"),
                List.of()
        );
        FakeMerchantLookupService merchantLookupService = new FakeMerchantLookupService(merchant);
        FakePersistenceService persistenceService = new FakePersistenceService(null, emptySearchCache);
        FakeWebSearchService webSearchService = new FakeWebSearchService();
        FakeValidationService validationService = new FakeValidationService();
        DiscountCodeSearchService service = new DiscountCodeSearchService(
                merchantLookupService,
                persistenceService,
                webSearchService,
                validationService,
                properties()
        );

        var result = service.search(command(merchantId));

        assertThat(result.cached()).isTrue();
        assertThat(result.codes()).isEmpty();
        assertThat(result.searchedAt()).isEqualTo(Instant.parse("2026-07-03T10:00:00Z"));
        assertThat(webSearchService.called).isFalse();
        assertThat(validationService.called).isFalse();
        assertThat(persistenceService.saveCalled).isFalse();
    }

    private SearchDiscountCodesCommand command(UUID merchantId) {
        return new SearchDiscountCodesCommand(
                UUID.randomUUID(),
                merchantId,
                null,
                List.of(new SearchDiscountCodesCommand.Item("gid://shopify/ProductVariant/1", 1)),
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private DiscountCodeSearchProperties properties() {
        return new DiscountCodeSearchProperties(
                Duration.ofHours(48),
                Duration.ofHours(12),
                Duration.ofHours(1),
                10,
                "test-model:online",
                8
        );
    }

    private DiscountMerchant merchant(UUID merchantId) {
        return new DiscountMerchant(
                merchantId,
                "merchant.example",
                "Merchant",
                "https://merchant.example/api/mcp",
                null,
                false
        );
    }

    private static class FakeMerchantLookupService extends DiscountMerchantLookupService {

        private final DiscountMerchant merchant;

        FakeMerchantLookupService(DiscountMerchant merchant) {
            super(null);
            this.merchant = merchant;
        }

        @Override
        public DiscountMerchant find(UUID merchantId, String merchantDomain) {
            return merchant;
        }
    }

    private static class FakePersistenceService extends DiscountCodePersistenceService {

        private final DiscountCodeCacheResult cacheResult;
        private final DiscountCodeCacheResult freshSearchResult;
        private boolean saveCalled;

        FakePersistenceService(DiscountCodeCacheResult cacheResult, DiscountCodeCacheResult freshSearchResult) {
            super(null, null, null);
            this.cacheResult = cacheResult;
            this.freshSearchResult = freshSearchResult;
        }

        @Override
        public Optional<DiscountCodeCacheResult> findFreshValidCodes(UUID merchantId, Instant now) {
            return Optional.ofNullable(cacheResult);
        }

        @Override
        public Optional<DiscountCodeCacheResult> findFreshSearch(UUID merchantId, Instant now) {
            return Optional.ofNullable(freshSearchResult);
        }

        @Override
        public Map<String, CachedDiscountCodeCandidate> findFreshNonValidCandidates(
                UUID merchantId,
                java.util.Collection<String> normalizedCodes,
                Instant now
        ) {
            return Map.of();
        }

        @Override
        public void saveSearch(
                DiscountMerchant merchant,
                DiscountCodeSearchStatus status,
                int sourceCount,
                String errorMessage,
                Instant searchedAt,
                Instant expiresAt,
                List<DiscountCodeCandidateEvaluation> evaluations
        ) {
            saveCalled = true;
        }
    }

    private static class FakeWebSearchService extends DiscountCodeWebSearchService {

        private boolean called;

        FakeWebSearchService() {
            super(null, null, null, null);
        }

        @Override
        public List<DiscountCodeCandidateSource> search(
                DiscountMerchant merchant,
                SearchDiscountCodesCommand command,
                Instant now
        ) {
            called = true;
            return List.of();
        }
    }

    private static class FakeValidationService extends DiscountCodeValidationService {

        private boolean called;

        FakeValidationService() {
            super(null, null);
        }

        @Override
        public DiscountCodeCandidateEvaluation validate(
                DiscountMerchant merchant,
                SearchDiscountCodesCommand command,
                DiscountCodeCandidateSource candidate,
                Instant now
        ) {
            called = true;
            return new DiscountCodeCandidateEvaluation(
                    candidate,
                    DiscountCodeStatus.INVALID,
                    null,
                    now,
                    now,
                    ""
            );
        }
    }
}
