package com.meant.api.module.agent.repository;

import com.meant.api.module.agent.entity.AgentProductInteraction;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentProductInteractionRepository extends JpaRepository<AgentProductInteraction, UUID> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into agent_product_interaction (
                id, user_id, canonical_product_key, pinned, watched, created_at, updated_at, version
            ) values (
                :id, :userId, :canonicalProductKey, false, false, :now, :now, 0
            )
            on conflict (user_id, canonical_product_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("canonicalProductKey") String canonicalProductKey,
            @Param("now") java.time.Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select interaction from AgentProductInteraction interaction
            where interaction.userId = :userId
              and interaction.canonicalProductKey = :canonicalProductKey
            """)
    Optional<AgentProductInteraction> findForUpdate(
            @Param("userId") UUID userId,
            @Param("canonicalProductKey") String canonicalProductKey
    );

    @Query("""
            select interaction from AgentProductInteraction interaction
            where interaction.userId = :userId
              and (interaction.pinned = true or interaction.watched = true)
            order by interaction.updatedAt desc
            """)
    List<AgentProductInteraction> findActiveByUserId(
            @Param("userId") UUID userId,
            Pageable pageable
    );
}
