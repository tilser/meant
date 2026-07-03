package com.meant.api.module.discount.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.discount.constant.DiscountCodeSearchStatus;
import com.meant.api.module.discount.entity.DiscountCodeSearch;
import com.meant.api.module.discount.repository.DiscountCodeSearchRepository;
import com.meant.api.module.discount.service.dto.DiscountCodeSearchCacheResult;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscountCodePersistenceServiceTest {

    @Test
    void freshSearchPreservesSearchStatus() {
        assertThat(findFreshSearch(DiscountCodeSearchStatus.COMPLETED))
                .get()
                .extracting(DiscountCodeSearchCacheResult::status)
                .isEqualTo(DiscountCodeSearchStatus.COMPLETED);
        assertThat(findFreshSearch(DiscountCodeSearchStatus.NO_CODES_FOUND))
                .get()
                .extracting(DiscountCodeSearchCacheResult::status)
                .isEqualTo(DiscountCodeSearchStatus.NO_CODES_FOUND);
        assertThat(findFreshSearch(DiscountCodeSearchStatus.FAILED_RETRYABLE))
                .get()
                .extracting(DiscountCodeSearchCacheResult::status)
                .isEqualTo(DiscountCodeSearchStatus.FAILED_RETRYABLE);
        assertThat(findFreshSearch(DiscountCodeSearchStatus.FAILED_PERMANENT))
                .get()
                .extracting(DiscountCodeSearchCacheResult::status)
                .isEqualTo(DiscountCodeSearchStatus.FAILED_PERMANENT);
    }

    private Optional<DiscountCodeSearchCacheResult> findFreshSearch(DiscountCodeSearchStatus status) {
        UUID merchantId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-03T10:00:00Z");
        Instant searchedAt = Instant.parse("2026-07-03T09:00:00Z");
        Instant expiresAt = Instant.parse("2026-07-03T11:00:00Z");
        DiscountCodeSearchRepository searchRepository = searchRepository(DiscountCodeSearch.builder()
                .status(status)
                .searchedAt(searchedAt)
                .expiresAt(expiresAt)
                .build());
        DiscountCodePersistenceService service = new DiscountCodePersistenceService(searchRepository, null, null);

        return service.findFreshSearch(merchantId, now);
    }

    private DiscountCodeSearchRepository searchRepository(DiscountCodeSearch search) {
        return (DiscountCodeSearchRepository) Proxy.newProxyInstance(
                DiscountCodeSearchRepository.class.getClassLoader(),
                new Class<?>[]{DiscountCodeSearchRepository.class},
                (proxy, method, args) -> {
                    if ("findFirstByMerchant_IdAndExpiresAtAfterOrderBySearchedAtDesc".equals(method.getName())) {
                        return Optional.of(search);
                    }
                    if ("toString".equals(method.getName())) {
                        return "FakeDiscountCodeSearchRepository";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
