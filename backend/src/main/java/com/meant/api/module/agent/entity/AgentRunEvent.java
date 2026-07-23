package com.meant.api.module.agent.entity;

import com.meant.api.common.entity.AssignedIdEntity;
import com.meant.api.module.agent.constant.AgentRunEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "agent_run_event")
public class AgentRunEvent extends AssignedIdEntity<UUID> {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID runId;

    @Column(nullable = false, updatable = false)
    private UUID conversationId;

    @Column(nullable = false, updatable = false)
    private long cursor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AgentRunEventType eventType;

    @Column(nullable = false, updatable = false)
    private int schemaVersion;

    @Column(nullable = false, updatable = false)
    private String payloadJson;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;
}
