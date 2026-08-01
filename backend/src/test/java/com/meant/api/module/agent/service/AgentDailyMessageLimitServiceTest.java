package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentMessageLimitProperties;
import com.meant.api.module.agent.repository.AgentDailyMessageUsageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AgentDailyMessageLimitServiceTest {

    @Test
    void reservesUsageForTheUtcCalendarDate() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-01T23:30:00Z");
        AgentDailyMessageUsageRepository repository = mock(AgentDailyMessageUsageRepository.class);
        when(repository.reserve(userId, LocalDate.of(2026, 8, 1), 100, now)).thenReturn(true);
        AgentDailyMessageLimitService service = new AgentDailyMessageLimitService(
                repository,
                new AgentMessageLimitProperties(100),
                Clock.fixed(now, ZoneOffset.ofHours(12))
        );

        service.reserve(userId);

        verify(repository).reserve(userId, LocalDate.of(2026, 8, 1), 100, now);
    }

    @Test
    void rejectsUsageAfterTheConfiguredLimitIsFull() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        AgentDailyMessageUsageRepository repository = mock(AgentDailyMessageUsageRepository.class);
        when(repository.reserve(userId, LocalDate.of(2026, 8, 1), 100, now)).thenReturn(false);
        AgentDailyMessageLimitService service = new AgentDailyMessageLimitService(
                repository,
                new AgentMessageLimitProperties(100),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.reserve(userId))
                .isInstanceOfSatisfying(AgentException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AGENT_DAILY_MESSAGE_LIMIT);
                    assertThat(exception.getSafeMessage())
                            .isEqualTo("You've reached today's limit of 100 messages. "
                                    + "Come back tomorrow to continue shopping.");
                });
    }
}
