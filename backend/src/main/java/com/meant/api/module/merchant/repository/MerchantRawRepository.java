package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.constant.MerchantRawSource;
import com.meant.api.module.merchant.entity.MerchantRaw;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantRawRepository extends JpaRepository<MerchantRaw, UUID> {

    Optional<MerchantRaw> findBySourceAndDomain(MerchantRawSource source, String domain);

    List<MerchantRaw> findBySourceAndDomainIn(MerchantRawSource source, Collection<String> domains);

    List<MerchantRaw> findByDomainIn(Collection<String> domains);

    @Query("""
            select merchantRaw.domain from MerchantRaw merchantRaw
            where merchantRaw.source = :source
              and merchantRaw.active = true
            """)
    List<String> findActiveDomainsBySource(@Param("source") MerchantRawSource source);

    @Modifying
    @Query("""
            update MerchantRaw merchantRaw
            set merchantRaw.active = false,
                merchantRaw.processed = false,
                merchantRaw.processingStatus = 'INACTIVE',
                merchantRaw.processingError = null
            where merchantRaw.source = :source
              and merchantRaw.active = true
            """)
    int markAllActiveBySourceInactive(@Param("source") MerchantRawSource source);

    @Modifying
    @Query("""
            update MerchantRaw merchantRaw
            set merchantRaw.active = false,
                merchantRaw.processed = false,
                merchantRaw.processingStatus = 'INACTIVE',
                merchantRaw.processingError = null
            where merchantRaw.source = :source
              and merchantRaw.active = true
              and merchantRaw.domain in :domains
            """)
    int markInactiveBySourceAndDomainIn(
            @Param("source") MerchantRawSource source,
            @Param("domains") Collection<String> domains
    );

    @Query("""
            select merchantRaw from MerchantRaw merchantRaw
            where merchantRaw.active = true
              and merchantRaw.processed = false
              and (
                merchantRaw.processedAt is null
                or merchantRaw.processedAt <= :retryBefore
              )
            order by
              merchantRaw.processedAt asc nulls first,
              merchantRaw.fetchedAt asc
            """)
    List<MerchantRaw> findUnprocessedActive(
            @Param("retryBefore") Instant retryBefore,
            Pageable pageable
    );
}
