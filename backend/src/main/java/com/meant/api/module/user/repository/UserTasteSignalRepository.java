package com.meant.api.module.user.repository;

import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.constant.UserTasteSuggestionStatus;
import com.meant.api.module.user.entity.UserTasteSignal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTasteSignalRepository extends JpaRepository<UserTasteSignal, UUID> {

    List<UserTasteSignal> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<UserTasteSignal> findByUserIdAndSignalTypeAndSignalKey(
            UUID userId,
            UserTasteSignalType signalType,
            String signalKey
    );

    List<UserTasteSignal> findByUserIdAndSignalTypeInAndSignalKeyIn(
            UUID userId,
            Collection<UserTasteSignalType> signalTypes,
            Collection<String> signalKeys
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            update UserTasteSignal signal
            set signal.suggestionStatus = :status,
                signal.updatedAt = :now
            where signal.userId = :userId
              and signal.suggestedFilterId = :suggestedFilterId
            """)
    int updateSuggestionStatus(
            @Param("userId") UUID userId,
            @Param("suggestedFilterId") String suggestedFilterId,
            @Param("status") UserTasteSuggestionStatus status,
            @Param("now") Instant now
    );

    long deleteByUserIdAndId(UUID userId, UUID id);
}
