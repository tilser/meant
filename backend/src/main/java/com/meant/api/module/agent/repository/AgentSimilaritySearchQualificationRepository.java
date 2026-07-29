package com.meant.api.module.agent.repository;

import com.meant.api.module.agent.entity.AgentSimilaritySearchQualification;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentSimilaritySearchQualificationRepository
        extends JpaRepository<AgentSimilaritySearchQualification, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into agent_similarity_search_qualifications (
                id,
                user_id,
                conversation_id,
                merchant_id,
                canonical_product_key,
                inventory_item_id,
                anchor_label,
                initial_user_text,
                created_at
            ) values (
                :qualificationId,
                :userId,
                :conversationId,
                :merchantId,
                :canonicalProductKey,
                :inventoryItemId,
                :anchorLabel,
                :initialUserText,
                :createdAt
            )
            on conflict (id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("qualificationId") UUID qualificationId,
            @Param("userId") UUID userId,
            @Param("conversationId") UUID conversationId,
            @Param("merchantId") UUID merchantId,
            @Param("canonicalProductKey") String canonicalProductKey,
            @Param("inventoryItemId") UUID inventoryItemId,
            @Param("anchorLabel") String anchorLabel,
            @Param("initialUserText") String initialUserText,
            @Param("createdAt") Instant createdAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select binding
            from AgentSimilaritySearchQualification binding
            where binding.id = :qualificationId
            """)
    Optional<AgentSimilaritySearchQualification> findByIdForUpdate(
            @Param("qualificationId") UUID qualificationId
    );
}
