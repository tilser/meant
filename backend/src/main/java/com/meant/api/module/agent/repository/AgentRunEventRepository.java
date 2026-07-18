package com.meant.api.module.agent.repository;

import com.meant.api.module.agent.entity.AgentRunEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunEventRepository extends JpaRepository<AgentRunEvent, UUID> {

    List<AgentRunEvent> findByRunIdAndCursorGreaterThanOrderByCursorAsc(
            UUID runId,
            long afterCursor,
            Pageable pageable
    );

    Optional<AgentRunEvent> findFirstByRunIdOrderByCursorAsc(UUID runId);

    Optional<AgentRunEvent> findFirstByRunIdOrderByCursorDesc(UUID runId);

    long deleteByOccurredAtBefore(Instant cutoff);
}
