package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserAssistantMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAssistantMessageRepository extends JpaRepository<UserAssistantMessage, UUID> {

    List<UserAssistantMessage> findTop12ByConversationIdAndUserIdOrderByCreatedAtDesc(UUID conversationId, UUID userId);

    List<UserAssistantMessage> findTop50ByConversationIdAndUserIdOrderByCreatedAtDesc(UUID conversationId, UUID userId);
}
