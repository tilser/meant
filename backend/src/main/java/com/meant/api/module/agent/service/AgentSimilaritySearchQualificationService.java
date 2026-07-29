package com.meant.api.module.agent.service;

import com.meant.api.module.agent.entity.AgentSimilaritySearchQualification;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentSimilaritySearchQualificationRepository;
import com.meant.api.module.agent.service.command.BindAgentSimilaritySearchQualificationCommand;
import com.meant.api.module.agent.service.dto.AgentSimilaritySearchQualificationContext;
import com.meant.api.module.agent.service.query.GetAgentSimilaritySearchQualificationQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class AgentSimilaritySearchQualificationService {

    private final AgentConversationRepository conversationRepository;
    private final AgentSimilaritySearchQualificationRepository repository;
    private final Clock clock;

    /**
     * Idempotently pre-binds a deterministic qualification ID to one exact similarity anchor.
     *
     * <p>This commits before remote qualification. The insert-on-conflict plus locked identity
     * check makes retries converge on the same binding while rejecting reuse of the deterministic
     * ID for a different anchor or owner.</p>
     */
    @Transactional
    public AgentSimilaritySearchQualificationContext bind(
            @NotNull @Valid BindAgentSimilaritySearchQualificationCommand command
    ) {
        requireOwnedConversation(command.userId(), command.conversationId(), command.merchantId(), true);
        repository.insertIfAbsent(
                command.qualificationId(),
                command.userId(),
                command.conversationId(),
                command.merchantId(),
                command.canonicalProductKey(),
                command.inventoryItemId(),
                command.anchorLabel(),
                command.initialUserText(),
                clock.instant()
        );
        AgentSimilaritySearchQualification binding = repository
                .findByIdForUpdate(command.qualificationId())
                .orElseThrow(() -> AgentException.conflict(
                        "The similarity-search qualification binding changed concurrently. Try again."));
        validateIdentity(binding, command);
        return result(binding);
    }

    @Transactional(readOnly = true)
    public Optional<AgentSimilaritySearchQualificationContext> find(
            @NotNull @Valid GetAgentSimilaritySearchQualificationQuery query
    ) {
        requireOwnedConversation(query.userId(), query.conversationId(), query.merchantId(), false);
        return repository.findById(query.qualificationId())
                .map(binding -> {
                    if (!binding.getUserId().equals(query.userId())
                            || !binding.getConversationId().equals(query.conversationId())
                            || !Objects.equals(binding.getMerchantId(), query.merchantId())) {
                        throw AgentException.notFound();
                    }
                    return result(binding);
                });
    }

    private void requireOwnedConversation(
            java.util.UUID userId,
            java.util.UUID conversationId,
            java.util.UUID merchantId,
            boolean lock
    ) {
        var conversation = (lock
                        ? conversationRepository.findOwnedForUpdate(conversationId, userId)
                        : conversationRepository.findByIdAndUserId(conversationId, userId))
                .orElseThrow(AgentException::notFound);
        if (!Objects.equals(conversation.getMerchantId(), merchantId)) {
            throw AgentException.notFound();
        }
    }

    private void validateIdentity(
            AgentSimilaritySearchQualification binding,
            BindAgentSimilaritySearchQualificationCommand command
    ) {
        if (!binding.getUserId().equals(command.userId())
                || !binding.getConversationId().equals(command.conversationId())
                || !Objects.equals(binding.getMerchantId(), command.merchantId())
                || !binding.getCanonicalProductKey().equals(command.canonicalProductKey())
                || !Objects.equals(binding.getInventoryItemId(), command.inventoryItemId())
                || !Objects.equals(binding.getAnchorLabel(), command.anchorLabel())
                || !binding.getInitialUserText().equals(command.initialUserText())) {
            throw AgentException.conflict(
                    "The product-search qualification is already bound to a different similarity request.");
        }
    }

    private AgentSimilaritySearchQualificationContext result(
            AgentSimilaritySearchQualification binding
    ) {
        return new AgentSimilaritySearchQualificationContext(
                binding.getId(),
                binding.getUserId(),
                binding.getConversationId(),
                binding.getMerchantId(),
                binding.getCanonicalProductKey(),
                binding.getInventoryItemId(),
                binding.getAnchorLabel(),
                binding.getInitialUserText(),
                binding.getCreatedAt()
        );
    }
}
