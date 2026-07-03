package com.meant.api.module.review.repository;

import com.meant.api.module.review.service.dto.ReviewProviderDiscoveryCandidate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReviewProviderDiscoveryCandidateRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<ReviewProviderDiscoveryCandidate> findCandidates(Instant now, int limit) {
        return jdbcTemplate.query("""
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
                """, new MapSqlParameterSource()
                .addValue("retryableStatuses", List.of("FAILED_RETRYABLE", "NOT_FOUND"))
                .addValue("now", Timestamp.from(now))
                .addValue("limit", limit), (resultSet, _) -> new ReviewProviderDiscoveryCandidate(
                resultSet.getObject("merchant_id", java.util.UUID.class),
                resultSet.getString("merchant_domain")
        ));
    }
}
