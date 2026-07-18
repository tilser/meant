package com.meant.api.module.agent.repository;

import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.constant.AgentMessageRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, UUID> {

    List<AgentMessage> findByConversationIdOrderBySequenceNumberAsc(UUID conversationId, Pageable pageable);

    List<AgentMessage> findByConversationIdOrderBySequenceNumberDesc(UUID conversationId, Pageable pageable);

    List<AgentMessage> findByConversationIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
            UUID conversationId,
            long afterSequence,
            Pageable pageable
    );

    List<AgentMessage> findByRunIdOrderBySequenceNumberAsc(UUID runId);

    List<AgentMessage> findByConversationIdAndSequenceNumberGreaterThanAndSequenceNumberLessThanEqualOrderBySequenceNumberAsc(
            UUID conversationId,
            long afterSequence,
            long throughSequence
    );

    Optional<AgentMessage> findByConversationIdAndRoleAndCorrelationId(
            UUID conversationId,
            AgentMessageRole role,
            String correlationId
    );
}
