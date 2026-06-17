package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserAssistantConversation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAssistantConversationRepository extends JpaRepository<UserAssistantConversation, UUID> {

    Optional<UserAssistantConversation> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserAssistantConversation> findFirstByUserIdOrderByUpdatedAtDesc(UUID userId);

    List<UserAssistantConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId, Pageable pageable);
}
