package com.meant.api.module.review.repository;

import com.meant.api.module.review.service.dto.ReviewProviderDiscoveryCandidate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ReviewProviderDiscoveryCandidateRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
    public List<ReviewProviderDiscoveryCandidate> claimCandidates(Instant now, Instant claimExpiresAt, int limit) {
        List<ReviewProviderDiscoveryCandidate> candidates = jdbcTemplate.query("""
                select merchant.id as merchant_id,
                       merchant.domain as merchant_domain
                from merchant merchant
                left join review_provider provider on provider.merchant_id = merchant.id
                where merchant.active = true
                  and (
                    provider.id is null
                    or (
                      provider.status in (:retryableStatuses)
                      and provider.next_check_at <= :now
                    )
                  )
                order by coalesce(provider.next_check_at, merchant.updated_at) asc
                limit :limit
                for update of merchant skip locked
                """, new MapSqlParameterSource()
                .addValue("retryableStatuses", List.of("FAILED_RETRYABLE", "NOT_FOUND"))
                .addValue("now", Timestamp.from(now))
                .addValue("limit", limit), (resultSet, _) -> new ReviewProviderDiscoveryCandidate(
                resultSet.getObject("merchant_id", java.util.UUID.class),
                resultSet.getString("merchant_domain")
        ));
        List<ReviewProviderDiscoveryCandidate> claimed = new ArrayList<>();
        for (ReviewProviderDiscoveryCandidate candidate : candidates) {
            if (claim(candidate, now, claimExpiresAt)) {
                claimed.add(candidate);
            }
        }
        return claimed;
    }

    private boolean claim(
            ReviewProviderDiscoveryCandidate candidate,
            Instant now,
            Instant claimExpiresAt
    ) {
        int updated = jdbcTemplate.update("""
                update review_provider
                set merchant_domain = :merchantDomain,
                    status = 'FAILED_RETRYABLE',
                    next_check_at = :claimExpiresAt,
                    error_message = null,
                    updated_at = :now
                where merchant_id = :merchantId
                  and status in (:retryableStatuses)
                  and next_check_at <= :now
                """, parameters(candidate, now, claimExpiresAt));
        if (updated > 0) {
            return true;
        }

        int inserted = jdbcTemplate.update("""
                insert into review_provider (
                    id,
                    merchant_id,
                    merchant_domain,
                    provider,
                    status,
                    provider_key,
                    product_id_type,
                    source_url,
                    evidence,
                    last_checked_at,
                    next_check_at,
                    error_message,
                    created_at,
                    updated_at
                )
                values (
                    :id,
                    :merchantId,
                    :merchantDomain,
                    'UNKNOWN',
                    'FAILED_RETRYABLE',
                    null,
                    'UNKNOWN',
                    null,
                    null,
                    null,
                    :claimExpiresAt,
                    null,
                    :now,
                    :now
                )
                on conflict (merchant_id) do nothing
                """, parameters(candidate, now, claimExpiresAt).addValue("id", UUID.randomUUID()));
        return inserted > 0;
    }

    private MapSqlParameterSource parameters(
            ReviewProviderDiscoveryCandidate candidate,
            Instant now,
            Instant claimExpiresAt
    ) {
        return new MapSqlParameterSource()
                .addValue("merchantId", candidate.merchantId())
                .addValue("merchantDomain", candidate.merchantDomain())
                .addValue("retryableStatuses", List.of("FAILED_RETRYABLE", "NOT_FOUND"))
                .addValue("claimExpiresAt", Timestamp.from(claimExpiresAt))
                .addValue("now", Timestamp.from(now));
    }
}
