package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserAssistantConversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAssistantConversationRepository extends JpaRepository<UserAssistantConversation, UUID> {

    Optional<UserAssistantConversation> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserAssistantConversation> findFirstByUserIdOrderByUpdatedAtDesc(UUID userId);
}
