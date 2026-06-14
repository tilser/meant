package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.MerchantRaw;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

public interface MerchantRawRepository extends JpaRepository<MerchantRaw, UUID> {

    Optional<MerchantRaw> findByDomain(String domain);

    List<MerchantRaw> findByDomainIn(Collection<String> domains);

    @Query("""
            select merchantRaw from MerchantRaw merchantRaw
            where merchantRaw.active = true and merchantRaw.processed = false
            order by merchantRaw.processingError desc
            """)
    List<MerchantRaw> findUnprocessedActive(Pageable pageable);
}
