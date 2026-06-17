package com.meant.api.module.user.entity;

import com.meant.api.module.user.constant.UserAssistantMessageRole;
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
@Table(name = "user_assistant_messages")
public class UserAssistantMessage implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID conversationId;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserAssistantMessageRole role;

    @Column(nullable = false)
    private String content;

    private String model;

    private String pageContext;

    private String productsJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    public static UserAssistantMessage create(
            UUID conversationId,
            UUID userId,
            UserAssistantMessageRole role,
            String content,
            String model,
            String pageContext,
            String productsJson,
            Instant now
    ) {
        return UserAssistantMessage.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .userId(userId)
                .role(role)
                .content(content)
                .model(model)
                .pageContext(pageContext)
                .productsJson(productsJson)
                .createdAt(now)
                .build();
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
