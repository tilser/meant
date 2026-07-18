package com.meant.api.module.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "agent_product_interaction")
public class AgentProductInteraction {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private String canonicalProductKey;

    @Column(nullable = false)
    private boolean pinned;

    private String pinnedOfferKey;

    private Instant pinnedAt;

    @Column(nullable = false)
    private boolean watched;

    private String watchedOfferKey;

    private Instant watchedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static AgentProductInteraction createPinned(
            UUID userId,
            String canonicalProductKey,
            String offerKey,
            Instant now
    ) {
        return AgentProductInteraction.builder()
                .userId(userId)
                .canonicalProductKey(canonicalProductKey)
                .pinned(true)
                .pinnedOfferKey(offerKey)
                .pinnedAt(now)
                .watched(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public static AgentProductInteraction createWatched(
            UUID userId,
            String canonicalProductKey,
            String offerKey,
            Instant now
    ) {
        return AgentProductInteraction.builder()
                .userId(userId)
                .canonicalProductKey(canonicalProductKey)
                .pinned(false)
                .watched(true)
                .watchedOfferKey(offerKey)
                .watchedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public boolean pin(String offerKey, Instant now) {
        if (pinned) {
            return false;
        }
        pinned = true;
        pinnedOfferKey = offerKey;
        pinnedAt = now;
        updatedAt = now;
        return true;
    }

    public boolean unpin(Instant now) {
        if (!pinned) {
            return false;
        }
        pinned = false;
        pinnedOfferKey = null;
        pinnedAt = null;
        updatedAt = now;
        return true;
    }

    public boolean watch(String offerKey, Instant now) {
        if (watched) {
            return false;
        }
        watched = true;
        watchedOfferKey = offerKey;
        watchedAt = now;
        updatedAt = now;
        return true;
    }

    public boolean unwatch(Instant now) {
        if (!watched) {
            return false;
        }
        watched = false;
        watchedOfferKey = null;
        watchedAt = null;
        updatedAt = now;
        return true;
    }
}
