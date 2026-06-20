package com.meant.api.module.user.repository;

import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.entity.UserTasteSignal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTasteSignalRepository extends JpaRepository<UserTasteSignal, UUID> {

    List<UserTasteSignal> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<UserTasteSignal> findByUserIdAndSignalTypeAndSignalKey(
            UUID userId,
            UserTasteSignalType signalType,
            String signalKey
    );

    List<UserTasteSignal> findByUserIdAndSuggestedFilterId(UUID userId, String suggestedFilterId);

    long deleteByUserIdAndId(UUID userId, UUID id);
}
