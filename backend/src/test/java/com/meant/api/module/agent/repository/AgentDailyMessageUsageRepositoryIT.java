package com.meant.api.module.agent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AgentDailyMessageUsageRepositoryIT extends PostgresIntegrationTestSupport {

    @Autowired
    private AgentDailyMessageUsageRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void atomicallyCapsConcurrentReservationsAndKeepsUtcDatesIndependent() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-01T23:59:00Z");
        LocalDate usageDate = LocalDate.of(2026, 8, 1);
        jdbcTemplate.update("""
                insert into users (id, email, created_at, updated_at)
                values (?, ?, ?, ?)
                """, userId, userId + "@example.test", Timestamp.from(now), Timestamp.from(now));

        var reservations = new ArrayList<Future<Boolean>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < 25; index++) {
                reservations.add(executor.submit(() -> repository.reserve(userId, usageDate, 10, now)));
            }
        }

        var results = new ArrayList<Boolean>();
        for (Future<Boolean> reservation : reservations) {
            results.add(reservation.get());
        }
        assertThat(results).containsExactlyInAnyOrder(
                true, true, true, true, true, true, true, true, true, true,
                false, false, false, false, false, false, false, false, false, false,
                false, false, false, false, false
        );
        assertThat(messageCount(userId, usageDate)).isEqualTo(10);

        LocalDate nextUtcDate = usageDate.plusDays(1);
        assertThat(repository.reserve(userId, nextUtcDate, 10, now.plusSeconds(60))).isTrue();
        assertThat(messageCount(userId, nextUtcDate)).isEqualTo(1);
    }

    private int messageCount(UUID userId, LocalDate usageDate) {
        return jdbcTemplate.queryForObject("""
                select message_count
                from agent_daily_message_usage
                where user_id = ? and usage_date = ?
                """, Integer.class, userId, usageDate);
    }
}
