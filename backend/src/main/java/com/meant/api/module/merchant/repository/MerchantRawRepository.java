package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.MerchantRaw;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

public interface MerchantRawRepository extends JpaRepository<MerchantRaw, UUID> {

    Optional<MerchantRaw> findByDomain(String domain);

    List<MerchantRaw> findByDomainIn(Collection<String> domains);

    @Query("""
            select merchantRaw from MerchantRaw merchantRaw
            where merchantRaw.active = true
              and merchantRaw.processed = false
              and (
                merchantRaw.processingStatus is null
                or merchantRaw.processingStatus <> :retryableStatus
                or merchantRaw.processedAt is null
                or merchantRaw.processedAt <= :retryBefore
              )
            order by
              case when merchantRaw.processedAt is null then 0 else 1 end,
              merchantRaw.processedAt asc,
              merchantRaw.fetchedAt asc
            """)
    List<MerchantRaw> findUnprocessedActive(
            @Param("retryableStatus") String retryableStatus,
            @Param("retryBefore") Instant retryBefore,
            Pageable pageable
    );
}
