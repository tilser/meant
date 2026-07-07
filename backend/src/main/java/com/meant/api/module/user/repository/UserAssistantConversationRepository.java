package com.meant.api.module.user.repository;

import com.meant.api.module.user.constant.UserAssistantConversationKind;
import com.meant.api.module.user.entity.UserAssistantConversation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAssistantConversationRepository extends JpaRepository<UserAssistantConversation, UUID> {

    Optional<UserAssistantConversation> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserAssistantConversation> findByIdAndUserIdAndKind(
            UUID id,
            UUID userId,
            UserAssistantConversationKind kind
    );

    Optional<UserAssistantConversation> findFirstByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<UserAssistantConversation> findFirstByUserIdAndKindOrderByUpdatedAtDesc(
            UUID userId,
            UserAssistantConversationKind kind
    );

    List<UserAssistantConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    List<UserAssistantConversation> findByUserIdAndKindOrderByUpdatedAtDesc(
            UUID userId,
            UserAssistantConversationKind kind,
            Pageable pageable
    );
}
