package com.meant.api.module.agent.service;

import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentMessageLimitProperties;
import com.meant.api.module.agent.repository.AgentDailyMessageUsageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentDailyMessageLimitService {

    private final AgentDailyMessageUsageRepository usageRepository;
    private final AgentMessageLimitProperties properties;
    private final Clock clock;

    public void reserve(UUID userId) {
        Instant now = clock.instant();
        boolean reserved = usageRepository.reserve(
                userId,
                now.atZone(ZoneOffset.UTC).toLocalDate(),
                properties.maximumPerUserPerDay(),
                now
        );
        if (!reserved) {
            throw AgentException.dailyMessageLimit(properties.maximumPerUserPerDay());
        }
    }
}
