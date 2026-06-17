package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserAssistantMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAssistantMessageRepository extends JpaRepository<UserAssistantMessage, UUID> {

    List<UserAssistantMessage> findByConversationIdAndUserIdOrderByCreatedAtDesc(
            UUID conversationId,
            UUID userId,
            Pageable pageable
    );
}
