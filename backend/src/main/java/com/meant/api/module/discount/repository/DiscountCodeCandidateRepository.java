package com.meant.api.module.discount.repository;

import com.meant.api.module.discount.constant.DiscountCodeStatus;
import com.meant.api.module.discount.entity.DiscountCodeCandidate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DiscountCodeCandidateRepository extends JpaRepository<DiscountCodeCandidate, UUID> {

    @EntityGraph(attributePaths = "search")
    @Query("""
            select candidate
            from DiscountCodeCandidate candidate
            where candidate.merchant.id = :merchantId
              and candidate.status = :status
              and candidate.expiresAt > :now
            order by candidate.search.searchedAt desc, candidate.displayOrder asc
            """)
    List<DiscountCodeCandidate> findFreshValid(
            UUID merchantId,
            DiscountCodeStatus status,
            Instant now
    );

    @Query("""
            select candidate
            from DiscountCodeCandidate candidate
            where candidate.merchant.id = :merchantId
              and candidate.status in :statuses
              and candidate.expiresAt > :now
              and lower(candidate.code) in :normalizedCodes
            order by candidate.expiresAt desc, candidate.updatedAt desc
            """)
    List<DiscountCodeCandidate> findFreshByNormalizedCodes(
            UUID merchantId,
            Collection<DiscountCodeStatus> statuses,
            Collection<String> normalizedCodes,
            Instant now
    );
}
