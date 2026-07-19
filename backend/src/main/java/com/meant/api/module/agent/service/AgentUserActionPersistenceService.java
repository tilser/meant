package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentConversationStatus;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.constant.AgentUserActionStatus;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.entity.AgentUserAction;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentUserActionRepository;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.command.RecordAgentUserActionCommand;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.AgentUserActionReservation;
import com.meant.api.module.agent.service.dto.AgentUserActionResult;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentUserActionPersistenceService {

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentUserActionRepository actionRepository;
    private final AgentArtifactReferenceRepository artifactRepository;
    private final AgentArtifactService artifactService;
    private final AgentConversationMemoryService memoryService;
    private final AgentJsonSupport jsonSupport;
    private final AgentProperties properties;
    private final Clock clock;

    @Transactional
    public AgentUserActionReservation reserve(
            RecordAgentUserActionCommand command,
            String argumentsJson,
            String toolVersion
    ) {
        AgentConversation conversation = conversationRepository.findOwnedForUpdate(
                        command.conversationId(),
                        command.userId()
                )
                .orElseThrow(AgentException::notFound);
        if (conversation.getStatus() == AgentConversationStatus.ARCHIVED) {
            throw AgentException.conflict("Archived conversations cannot accept new actions.");
        }
        var existing = actionRepository.findByUserIdAndConversationIdAndIdempotencyKey(
                command.userId(),
                command.conversationId(),
                command.idempotencyKey()
        );
        if (existing.isPresent()) {
            AgentUserAction action = existing.get();
            if (!action.getToolName().equals(command.toolName())
                    || !action.getArgumentsJson().equals(argumentsJson)) {
                throw AgentException.conflict(
                        "The action idempotency key was already used for a different tool contract or arguments.");
            }
            recoverStaleAction(action);
            if (action.getStatus() == AgentUserActionStatus.COMPLETED && action.getMessageId() != null) {
                AgentMessage message = messageRepository.findById(action.getMessageId())
                        .orElseThrow(AgentException::notFound);
                return new AgentUserActionReservation(
                        action.getId(),
                        false,
                        new AgentUserActionResult(
                                AgentResultMapper.message(message),
                                action.getResultJson(),
                                artifactRepository.findByMessageIdOrderByOrdinalAsc(message.getId()).stream()
                                        .map(AgentResultMapper::artifact)
                                        .toList()
                        )
                );
            }
            if (!action.getToolVersion().equals(toolVersion)) {
                if (action.getStatus() == AgentUserActionStatus.RESERVED
                        || action.getStatus() == AgentUserActionStatus.RUNNING) {
                    throw AgentException.actionInProgress();
                }
                if (action.getStatus() == AgentUserActionStatus.UNCERTAIN) {
                    throw AgentException.actionUncertain(
                            HttpStatus.CONFLICT,
                            "The original action outcome is still uncertain after a tool update."
                    );
                }
                throw AgentException.conflict(
                        "The action idempotency key was already used with a different tool version."
                );
            }
            if (action.getStatus() == AgentUserActionStatus.UNCERTAIN) {
                action.retry();
                return new AgentUserActionReservation(action.getId(), true, null);
            }
            if (action.getStatus() == AgentUserActionStatus.RESERVED
                    || action.getStatus() == AgentUserActionStatus.RUNNING) {
                throw AgentException.actionInProgress();
            }
            throw AgentException.conflict("This action is already in progress or cannot be retried.");
        }
        AgentUserAction action = actionRepository.save(AgentUserAction.builder()
                .userId(command.userId())
                .conversationId(command.conversationId())
                .toolName(command.toolName())
                .toolVersion(toolVersion)
                .argumentsJson(argumentsJson)
                .idempotencyKey(command.idempotencyKey())
                .status(AgentUserActionStatus.RESERVED)
                .createdAt(clock.instant())
                .build());
        return new AgentUserActionReservation(action.getId(), true, null);
    }

    @Transactional
    public void start(UUID actionId) {
        AgentUserAction action = actionRepository.findById(actionId).orElseThrow(AgentException::notFound);
        action.start(clock.instant());
    }

    @Transactional
    public AgentUserActionResult complete(
            UUID actionId,
            RecordAgentUserActionCommand command,
            AgentToolExecutionResult executionResult
    ) {
        AgentUserAction action = actionRepository.findById(actionId).orElseThrow(AgentException::notFound);
        AgentConversation conversation = conversationRepository.findOwnedForUpdate(
                        command.conversationId(),
                        command.userId()
                )
                .orElseThrow(AgentException::notFound);
        Instant now = clock.instant();
        AgentMessage message = messageRepository.save(AgentMessage.builder()
                .conversationId(conversation.getId())
                .role(AgentMessageRole.USER_ACTION)
                .contentKind(AgentContentKind.ACTION)
                .sequenceNumber(conversation.nextSequence(now))
                .textContent(safeTranscriptSummary(executionResult.safeSummary()))
                .contentJson(jsonSupport.bounded(executionResult.resultJson()))
                .correlationId(command.idempotencyKey())
                .createdAt(now)
                .build());
        var artifacts = artifactService.persist(
                conversation.getId(),
                null,
                message.getId(),
                null,
                executionResult.artifacts()
        );
        action.complete(message.getId(), jsonSupport.bounded(executionResult.resultJson()), now);
        memoryService.refresh(conversation);
        return new AgentUserActionResult(AgentResultMapper.message(message), action.getResultJson(), artifacts);
    }

    @Transactional
    public void fail(UUID actionId, AgentUserActionStatus status, String safeMessage) {
        actionRepository.findById(actionId)
                .orElseThrow(AgentException::notFound)
                .fail(status, safeMessage, clock.instant());
    }

    private String safeTranscriptSummary(String value) {
        String normalized = value == null
                ? ""
                : value.replaceAll("[\\p{Cc}&&[^\\n\\t]]", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
        if (normalized.isBlank()) {
            return "Action completed.";
        }
        int codePoints = normalized.codePointCount(0, normalized.length());
        return codePoints <= 500
                ? normalized
                : normalized.substring(0, normalized.offsetByCodePoints(0, 500));
    }

    private void recoverStaleAction(AgentUserAction action) {
        Instant now = clock.instant();
        if (action.getStatus() == AgentUserActionStatus.RUNNING
                && action.getStartedAt() != null
                && action.getStartedAt().isBefore(
                        AgentMutationRecoveryService.staleCutoff(now, properties.toolDeadline()))) {
            action.fail(
                    AgentUserActionStatus.UNCERTAIN,
                    AgentMutationRecoveryService.STALE_MUTATION_MESSAGE,
                    now
            );
        }
    }
}
