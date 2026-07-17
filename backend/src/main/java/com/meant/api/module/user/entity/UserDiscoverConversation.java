package com.meant.api.module.user.entity;

import com.meant.api.module.user.constant.UserConversationKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "user_discover_conversations")
public class UserDiscoverConversation implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String kind;

    private String payload;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private long revision;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    public static UserDiscoverConversation create(
            UUID id,
            UUID userId,
            String title,
            String payload,
            Instant now
    ) {
        return UserDiscoverConversation.builder()
                .id(id)
                .userId(userId)
                .title(title)
                .kind(UserConversationKind.DISCOVER.name())
                .payload(payload)
                .createdAt(now)
                .updatedAt(now)
                .revision(0)
                .build();
    }

    public void replaceSnapshot(String title, String payload, Instant now) {
        this.title = title;
        this.payload = payload;
        this.updatedAt = now;
        this.revision += 1;
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
