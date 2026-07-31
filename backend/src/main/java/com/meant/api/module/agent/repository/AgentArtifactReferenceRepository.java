package com.meant.api.module.agent.repository;

import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.constant.AgentArtifactType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentArtifactReferenceRepository extends JpaRepository<AgentArtifactReference, UUID> {

    List<AgentArtifactReference> findByConversationIdOrderByCreatedAtAscOrdinalAsc(
            UUID conversationId,
            Pageable pageable
    );

    List<AgentArtifactReference> findByConversationIdOrderByCreatedAtAscOrdinalAsc(UUID conversationId);

    List<AgentArtifactReference> findByConversationIdOrderByCreatedAtDescOrdinalAsc(
            UUID conversationId,
            Pageable pageable
    );

    List<AgentArtifactReference> findByMessageIdOrderByOrdinalAsc(UUID messageId);

    List<AgentArtifactReference> findByConversationIdAndMessageIdOrderByOrdinalAsc(
            UUID conversationId,
            UUID messageId
    );

    List<AgentArtifactReference> findByMessageIdInOrderByCreatedAtAscOrdinalAsc(List<UUID> messageIds);

    List<AgentArtifactReference> findByRunIdOrderByCreatedAtAscOrdinalAsc(UUID runId);

    List<AgentArtifactReference> findByToolInvocationIdOrderByOrdinalAsc(UUID toolInvocationId);

    boolean existsByRunIdAndArtifactTypeIn(UUID runId, List<AgentArtifactType> artifactTypes);

    Optional<AgentArtifactReference> findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
            UUID conversationId,
            String stableKey
    );

    Optional<AgentArtifactReference> findFirstByConversationIdAndOfferKeyOrderByCreatedAtDesc(
            UUID conversationId,
            String offerKey
    );

    List<AgentArtifactReference> findByConversationIdAndOfferKeyInOrderByCreatedAtDesc(
            UUID conversationId,
            List<String> offerKeys
    );
}
