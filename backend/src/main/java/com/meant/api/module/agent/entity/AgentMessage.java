package com.meant.api.module.agent.entity;

import com.meant.api.common.entity.AssignedIdEntity;
import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentMessageRole;
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
@Table(name = "agent_message")
public class AgentMessage extends AssignedIdEntity<UUID> {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID conversationId;

    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AgentMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AgentContentKind contentKind;

    @Column(nullable = false, updatable = false)
    private long sequenceNumber;

    @Column(updatable = false)
    private String textContent;

    @Column(updatable = false)
    private String contentJson;

    @Column(updatable = false)
    private String correlationId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public void linkRun(UUID linkedRunId) {
        if (runId != null && !runId.equals(linkedRunId)) {
            throw new IllegalStateException("Agent message is already linked to another run");
        }
        runId = linkedRunId;
    }
}
