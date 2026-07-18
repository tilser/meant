package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentRunEventType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentArtifactResult;
import com.meant.api.module.agent.service.dto.AgentEventPayload;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentArtifactService {

    private static final List<com.meant.api.module.agent.constant.AgentArtifactType> USEFUL_PROPOSAL_TYPES = List.of(
            com.meant.api.module.agent.constant.AgentArtifactType.PRODUCT,
            com.meant.api.module.agent.constant.AgentArtifactType.SAVED_PRODUCT,
            com.meant.api.module.agent.constant.AgentArtifactType.COMPARISON,
            com.meant.api.module.agent.constant.AgentArtifactType.MISSION,
            com.meant.api.module.agent.constant.AgentArtifactType.CART,
            com.meant.api.module.agent.constant.AgentArtifactType.CHECKOUT
    );

    private final AgentArtifactReferenceRepository artifactRepository;
    private final AgentRunRepository runRepository;
    private final AgentRunService runService;
    private final AgentMetrics metrics;
    private final Clock clock;

    @Transactional
    public List<AgentArtifactResult> persist(
            UUID conversationId,
            UUID runId,
            UUID messageId,
            UUID toolInvocationId,
            List<AgentArtifact> artifacts
    ) {
        return persist(conversationId, runId, null, messageId, toolInvocationId, artifacts);
    }

    @Transactional
    public List<AgentArtifactResult> persist(
            UUID conversationId,
            UUID runId,
            UUID executionOwner,
            UUID messageId,
            UUID toolInvocationId,
            List<AgentArtifact> artifacts
    ) {
        if (runId != null && executionOwner != null) {
            runService.requireOwnedExecution(runId, executionOwner);
        }
        Instant now = clock.instant();
        boolean firstUsefulProposal = runId != null
                && artifacts.stream().anyMatch(artifact -> USEFUL_PROPOSAL_TYPES.contains(artifact.type()))
                && !artifactRepository.existsByRunIdAndArtifactTypeIn(runId, USEFUL_PROPOSAL_TYPES);
        List<AgentArtifactResult> results = new ArrayList<>();
        for (AgentArtifact artifact : artifacts) {
            AgentArtifactReference stored = artifactRepository.save(AgentArtifactReference.builder()
                    .conversationId(conversationId)
                    .messageId(messageId)
                    .runId(runId)
                    .toolInvocationId(toolInvocationId)
                    .artifactType(artifact.type())
                    .ordinal(artifact.ordinal())
                    .stableKey(artifact.stableKey())
                    .label(artifact.label())
                    .canonicalProductKey(artifact.canonicalProductKey())
                    .offerKey(artifact.offerKey())
                    .inventoryItemId(artifact.inventoryItemId())
                    .cartId(artifact.cartId())
                    .cartLineId(artifact.cartLineId())
                    .checkoutAttemptId(artifact.checkoutAttemptId())
                    .payloadJson(artifact.payloadJson())
                    .createdAt(now)
                    .build());
            AgentArtifactResult result = AgentResultMapper.artifact(stored);
            results.add(result);
            if (runId != null) {
                if (executionOwner == null) {
                    runService.append(
                            runId,
                            AgentRunEventType.ARTIFACT_UPSERTED,
                            AgentEventPayload.artifact(result)
                    );
                } else {
                    runService.append(
                            runId,
                            executionOwner,
                            AgentRunEventType.ARTIFACT_UPSERTED,
                            AgentEventPayload.artifact(result)
                    );
                }
            }
        }
        if (firstUsefulProposal) {
            runRepository.findById(runId).ifPresent(run -> metrics.firstUsefulProposal(
                    artifacts.stream()
                            .map(AgentArtifact::type)
                            .filter(USEFUL_PROPOSAL_TYPES::contains)
                            .findFirst()
                            .map(Enum::name)
                            .orElse("unknown"),
                    java.time.Duration.between(
                            run.getStartedAt() == null ? run.getCreatedAt() : run.getStartedAt(), now)
            ));
        }
        return List.copyOf(results);
    }
}
