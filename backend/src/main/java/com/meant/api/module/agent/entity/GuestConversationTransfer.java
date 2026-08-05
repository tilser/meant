package com.meant.api.module.agent.entity;

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
@Table(name = "agent_guest_conversation_transfer")
public class GuestConversationTransfer {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private UUID guestUserId;

    @Column(nullable = false, updatable = false)
    private UUID conversationId;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant consumedAt;

    private UUID targetUserId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static GuestConversationTransfer issue(
            String tokenHash,
            UUID guestUserId,
            UUID conversationId,
            Instant expiresAt,
            Instant now
    ) {
        return GuestConversationTransfer.builder()
                .tokenHash(tokenHash)
                .guestUserId(guestUserId)
                .conversationId(conversationId)
                .expiresAt(expiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void rotate(String nextTokenHash, Instant nextExpiresAt, Instant now) {
        if (consumedAt != null) {
            throw new IllegalStateException("Consumed guest conversation transfers cannot be rotated");
        }
        tokenHash = nextTokenHash;
        expiresAt = nextExpiresAt;
        updatedAt = now;
    }

    public void consume(UUID targetUserId, Instant now) {
        if (consumedAt != null) {
            throw new IllegalStateException("Guest conversation transfer was already consumed");
        }
        this.targetUserId = targetUserId;
        consumedAt = now;
        updatedAt = now;
    }
}
