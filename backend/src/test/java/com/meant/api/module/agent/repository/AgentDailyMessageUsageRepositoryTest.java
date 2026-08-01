package com.meant.api.module.agent.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class AgentDailyMessageUsageRepositoryTest {

    @Test
    void atomicallyIncrementsUsageWhileTheDailyLimitHasCapacity() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        AgentDailyMessageUsageRepository repository = new AgentDailyMessageUsageRepository(jdbcTemplate);
        UUID userId = UUID.randomUUID();
        LocalDate usageDate = LocalDate.of(2026, 8, 1);
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        when(jdbcTemplate.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        boolean reserved = repository.reserve(userId, usageDate, 100, now);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parametersCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), parametersCaptor.capture());
        assertThat(reserved).isTrue();
        assertThat(sqlCaptor.getValue())
                .contains("on conflict (user_id, usage_date) do update")
                .contains("message_count < :maximumMessages");
        assertThat(parametersCaptor.getValue().getValue("userId")).isEqualTo(userId);
        assertThat(parametersCaptor.getValue().getValue("usageDate")).isEqualTo(Date.valueOf(usageDate));
        assertThat(parametersCaptor.getValue().getValue("maximumMessages")).isEqualTo(100);
        assertThat(parametersCaptor.getValue().getValue("updatedAt")).isEqualTo(Timestamp.from(now));
    }

    @Test
    void reportsNoReservationWhenTheDailyLimitIsFull() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        AgentDailyMessageUsageRepository repository = new AgentDailyMessageUsageRepository(jdbcTemplate);
        when(jdbcTemplate.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);

        boolean reserved = repository.reserve(
                UUID.randomUUID(),
                LocalDate.of(2026, 8, 1),
                100,
                Instant.parse("2026-08-01T12:00:00Z")
        );

        assertThat(reserved).isFalse();
    }
}
