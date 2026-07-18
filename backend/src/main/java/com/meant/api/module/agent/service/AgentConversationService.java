package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentConversationStatus;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.service.command.CreateAgentConversationCommand;
import com.meant.api.module.agent.service.command.UpdateAgentConversationCommand;
import com.meant.api.module.agent.service.dto.AgentConversationResult;
import com.meant.api.module.agent.service.dto.AgentConversationSummaryResult;
import com.meant.api.module.agent.service.query.GetAgentConversationQuery;
import com.meant.api.module.agent.service.query.ListAgentConversationsQuery;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class AgentConversationService {

    private static final String DEFAULT_TITLE = "New conversation";
    private static final int MAXIMUM_ARTIFACT_SNAPSHOT_SIZE = 500;

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentArtifactReferenceRepository artifactRepository;
    private final AgentRunRepository runRepository;
    private final Clock clock;

    @Transactional
    public AgentConversationSummaryResult create(@Valid CreateAgentConversationCommand command) {
        Instant now = clock.instant();
        String title = normalizeTitle(command.title());
        AgentConversation conversation = conversationRepository.save(
                AgentConversation.create(command.userId(), title, now)
        );
        return AgentResultMapper.conversation(conversation);
    }

    @Transactional(readOnly = true)
    public List<AgentConversationSummaryResult> list(@Valid ListAgentConversationsQuery query) {
        AgentConversationStatus status = query.archived()
                ? AgentConversationStatus.ARCHIVED
                : AgentConversationStatus.ACTIVE;
        return conversationRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(
                        query.userId(),
                        status,
                        PageRequest.of(0, query.limit())
                ).stream()
                .map(AgentResultMapper::conversation)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentConversationResult get(@Valid GetAgentConversationQuery query) {
        AgentConversation conversation = conversationRepository.findByIdAndUserId(
                        query.conversationId(),
                        query.userId()
                )
                .orElseThrow(AgentException::notFound);
        var messageEntities = messageRepository.findByConversationIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
                        conversation.getId(),
                        query.afterSequence(),
                        PageRequest.of(0, query.limit())
                );
        var messages = messageEntities.stream()
                .map(AgentResultMapper::message)
                .toList();
        var recentArtifacts = new ArrayList<>(artifactRepository
                .findByConversationIdOrderByCreatedAtDescOrdinalAsc(
                        conversation.getId(),
                        PageRequest.of(0, MAXIMUM_ARTIFACT_SNAPSHOT_SIZE)
                ));
        var artifactsById = new LinkedHashMap<UUID, com.meant.api.module.agent.entity.AgentArtifactReference>();
        if (!messageEntities.isEmpty()) {
            artifactRepository.findByMessageIdInOrderByCreatedAtAscOrdinalAsc(
                            messageEntities.stream()
                                    .map(com.meant.api.module.agent.entity.AgentMessage::getId)
                                    .toList()
                    )
                    .forEach(artifact -> artifactsById.put(artifact.getId(), artifact));
        }
        recentArtifacts.forEach(artifact -> artifactsById.putIfAbsent(artifact.getId(), artifact));
        var snapshotArtifacts = new ArrayList<>(artifactsById.values());
        snapshotArtifacts.sort(Comparator.comparing(com.meant.api.module.agent.entity.AgentArtifactReference::getCreatedAt)
                .thenComparingInt(com.meant.api.module.agent.entity.AgentArtifactReference::getOrdinal));
        var artifacts = snapshotArtifacts.stream()
                .map(AgentResultMapper::artifact)
                .toList();
        long latestCursor = runRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .map(run -> run.getLastEventCursor())
                .orElse(0L);
        return new AgentConversationResult(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getStatus(),
                conversation.getRollingSummary(),
                conversation.getSummaryVersion(),
                conversation.getActiveMissionId(),
                conversation.getLastSequenceNumber(),
                latestCursor,
                messages,
                artifacts,
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    @Transactional
    public AgentConversationSummaryResult update(@Valid UpdateAgentConversationCommand command) {
        AgentConversation conversation = conversationRepository.findOwnedForUpdate(
                        command.conversationId(),
                        command.userId()
                )
                .orElseThrow(AgentException::notFound);
        Instant now = clock.instant();
        if (command.title() != null) {
            conversation.rename(normalizeTitle(command.title()), now);
        }
        if (command.archived() != null) {
            conversation.archive(command.archived(), now);
        }
        return AgentResultMapper.conversation(conversation);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_TITLE;
        }
        return title.trim().replaceAll("\\s+", " ");
    }
}
