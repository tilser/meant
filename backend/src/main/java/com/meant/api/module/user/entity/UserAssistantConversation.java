package com.meant.api.module.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "user_assistant_conversations")
public class UserAssistantConversation {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static UserAssistantConversation create(UUID userId, String title, Instant now) {
        return UserAssistantConversation.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title(title)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }
}
