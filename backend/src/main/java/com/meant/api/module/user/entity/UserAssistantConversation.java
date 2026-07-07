package com.meant.api.module.user.entity;

import com.meant.api.module.user.constant.UserAssistantConversationKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "user_assistant_conversations")
public class UserAssistantConversation implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserAssistantConversationKind kind;

    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    public static UserAssistantConversation create(UUID userId, String title, Instant now) {
        return create(UUID.randomUUID(), userId, title, UserAssistantConversationKind.ASSISTANT, null, now);
    }

    public static UserAssistantConversation create(
            UUID id,
            UUID userId,
            String title,
            UserAssistantConversationKind kind,
            String payload,
            Instant now
    ) {
        return UserAssistantConversation.builder()
                .id(id)
                .userId(userId)
                .title(title)
                .kind(kind)
                .payload(payload)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void replaceSnapshot(String title, String payload, Instant now) {
        this.title = title;
        this.payload = payload;
        touch(now);
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
