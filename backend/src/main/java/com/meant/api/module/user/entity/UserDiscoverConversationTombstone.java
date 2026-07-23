package com.meant.api.module.user.entity;

import com.meant.api.common.entity.AssignedIdEntity;
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
@Table(name = "user_discover_conversation_tombstones")
public class UserDiscoverConversationTombstone extends AssignedIdEntity<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID conversationId;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private Instant deletedAt;

    @Override
    public UUID getId() {
        return conversationId;
    }

    public static UserDiscoverConversationTombstone create(UUID conversationId, UUID userId, Instant now) {
        return UserDiscoverConversationTombstone.builder()
                .conversationId(conversationId)
                .userId(userId)
                .deletedAt(now)
                .build();
    }
}
