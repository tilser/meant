package com.meant.api.module.agent.repository;

import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.constant.AgentMessageRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, UUID> {

    List<AgentMessage> findByConversationIdOrderBySequenceNumberAsc(UUID conversationId, Pageable pageable);

    @Query("""
            select message from AgentMessage message
            where message.conversationId = :conversationId
              and (message.role <> :userRole or message.sequenceNumber <= :throughSequence)
            order by message.sequenceNumber desc
            """)
    List<AgentMessage> findContextMessages(
            @Param("conversationId") UUID conversationId,
            @Param("userRole") AgentMessageRole userRole,
            @Param("throughSequence") long throughSequence,
            Pageable pageable
    );

    List<AgentMessage> findByConversationIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
            UUID conversationId,
            long afterSequence,
            Pageable pageable
    );

    List<AgentMessage> findByRunIdOrderBySequenceNumberAsc(UUID runId);

    Optional<AgentMessage> findFirstByConversationIdAndSequenceNumberLessThanOrderBySequenceNumberDesc(
            UUID conversationId,
            long sequenceNumber
    );

    List<AgentMessage> findByConversationIdAndSequenceNumberGreaterThanAndSequenceNumberLessThanEqualOrderBySequenceNumberAsc(
            UUID conversationId,
            long afterSequence,
            long throughSequence
    );

    @Query("""
            select message from AgentMessage message
            where message.conversationId = :conversationId
              and message.sequenceNumber <= :throughSequence
              and message.role in :roles
            order by message.sequenceNumber desc
            """)
    List<AgentMessage> findBuyerVisibleConversationMessages(
            @Param("conversationId") UUID conversationId,
            @Param("throughSequence") long throughSequence,
            @Param("roles") List<AgentMessageRole> roles,
            Pageable pageable
    );

    Optional<AgentMessage> findByConversationIdAndRoleAndCorrelationId(
            UUID conversationId,
            AgentMessageRole role,
            String correlationId
    );
}
