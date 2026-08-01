package com.meant.api.module.agent.repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AgentDailyMessageUsageRepository {

    private static final String RESERVE_MESSAGE_SQL = """
            insert into agent_daily_message_usage (
                user_id,
                usage_date,
                message_count,
                updated_at
            )
            values (
                :userId,
                :usageDate,
                1,
                :updatedAt
            )
            on conflict (user_id, usage_date) do update set
                message_count = agent_daily_message_usage.message_count + 1,
                updated_at = excluded.updated_at
            where agent_daily_message_usage.message_count < :maximumMessages
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public boolean reserve(UUID userId, LocalDate usageDate, int maximumMessages, Instant now) {
        int updatedRows = jdbcTemplate.update(RESERVE_MESSAGE_SQL, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("usageDate", Date.valueOf(usageDate))
                .addValue("maximumMessages", maximumMessages)
                .addValue("updatedAt", Timestamp.from(now)));
        return updatedRows == 1;
    }
}
