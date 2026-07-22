package com.meant.api.module.agent.entity;

import com.meant.api.module.agent.constant.AgentConversationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "agent_conversation")
public class AgentConversation {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID userId;

    private UUID merchantId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentConversationStatus status;

    private String rollingSummary;

    @Column(nullable = false)
    private int summaryVersion;

    private UUID activeMissionId;

    @Column(nullable = false)
    private long lastSequenceNumber;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static AgentConversation create(UUID userId, String title, Instant now) {
        return create(userId, title, null, now);
    }

    public static AgentConversation create(UUID userId, String title, UUID merchantId, Instant now) {
        return AgentConversation.builder()
                .userId(userId)
                .merchantId(merchantId)
                .title(title)
                .status(AgentConversationStatus.ACTIVE)
                .summaryVersion(0)
                .lastSequenceNumber(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public long nextSequence(Instant now) {
        lastSequenceNumber += 1;
        updatedAt = now;
        return lastSequenceNumber;
    }

    public void rename(String nextTitle, Instant now) {
        title = nextTitle;
        updatedAt = now;
    }

    public void archive(boolean archived, Instant now) {
        status = archived ? AgentConversationStatus.ARCHIVED : AgentConversationStatus.ACTIVE;
        updatedAt = now;
    }

    public void activateMission(UUID missionId, Instant now) {
        activeMissionId = missionId;
        updatedAt = now;
    }

    public void replaceSummary(String summary, int nextVersion, Instant now) {
        rollingSummary = summary;
        summaryVersion = nextVersion;
        updatedAt = now;
    }
}
