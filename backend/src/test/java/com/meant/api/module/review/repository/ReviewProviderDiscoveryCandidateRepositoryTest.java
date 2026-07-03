package com.meant.api.module.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.review.service.dto.ReviewProviderDiscoveryCandidate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class ReviewProviderDiscoveryCandidateRepositoryTest {

    @Test
    void claimCandidatesUsesConflictSafeInsertWhenConcurrentClaimWins() {
        ReviewProviderDiscoveryCandidate candidate = new ReviewProviderDiscoveryCandidate(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "merchant.example"
        );
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate(candidate);
        ReviewProviderDiscoveryCandidateRepository repository =
                new ReviewProviderDiscoveryCandidateRepository(jdbcTemplate);

        List<ReviewProviderDiscoveryCandidate> claimed = repository.claimCandidates(
                Instant.parse("2026-07-03T12:00:00Z"),
                Instant.parse("2026-07-03T12:05:00Z"),
                10
        );

        assertThat(claimed).isEmpty();
        assertThat(jdbcTemplate.updateSql).hasSize(2);
        assertThat(jdbcTemplate.updateSql.get(1)).contains("on conflict (merchant_id) do nothing");
    }

    private static class CapturingJdbcTemplate extends NamedParameterJdbcTemplate {

        private final ReviewProviderDiscoveryCandidate candidate;
        private final List<String> updateSql = new ArrayList<>();

        private CapturingJdbcTemplate(ReviewProviderDiscoveryCandidate candidate) {
            super(new JdbcTemplate());
            this.candidate = candidate;
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            return List.of((T) candidate);
        }

        @Override
        public int update(String sql, SqlParameterSource paramSource) {
            updateSql.add(sql);
            return 0;
        }
    }
}
