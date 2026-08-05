package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentConversationStatus;
import com.meant.api.module.agent.entity.AgentProductInteraction;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentProductInteractionRepository;
import com.meant.api.module.agent.service.dto.AgentProductInteractionReference;
import com.meant.api.module.agent.service.dto.AgentProductInteractionResult;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.user.exception.PermanentAccountRequiredException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentProductInteractionService {

    private final AgentConversationRepository conversationRepository;
    private final AgentProductInteractionRepository interactionRepository;
    private final AgentProductInteractionReferenceService referenceService;
    private final Clock clock;

    @Transactional
    public AgentProductInteractionResult pin(
            AgentToolExecutionContext context,
            String canonicalProductKey,
            String offerKey
    ) {
        AgentProductInteractionReference reference = validate(context, canonicalProductKey, offerKey);
        if (context.anonymousUser()
                && !interactionRepository.existsByUserIdAndCanonicalProductKeyAndPinnedTrue(
                        context.userId(), reference.canonicalProductKey())
                && interactionRepository.countByUserIdAndPinnedTrue(context.userId()) >= 2) {
            throw new PermanentAccountRequiredException();
        }
        Instant now = clock.instant();
        AgentProductInteraction interaction = findOrCreateForUpdate(context, reference, now);
        if (interaction.pin(reference.offerKey(), now)) {
            interactionRepository.save(interaction);
        }
        return result(interaction, reference.offerKey(), reference.label());
    }

    @Transactional
    public AgentProductInteractionResult unpin(
            AgentToolExecutionContext context,
            String canonicalProductKey,
            String offerKey
    ) {
        AgentProductInteractionReference reference = validate(context, canonicalProductKey, offerKey);
        return interactionRepository.findForUpdate(context.userId(), reference.canonicalProductKey())
                .map(interaction -> {
                    if (interaction.unpin(clock.instant())) {
                        interactionRepository.save(interaction);
                    }
                    return result(interaction, reference.offerKey(), reference.label());
                })
                .orElseGet(() -> inactive(reference));
    }

    @Transactional
    public AgentProductInteractionResult watch(
            AgentToolExecutionContext context,
            String canonicalProductKey,
            String offerKey
    ) {
        AgentProductInteractionReference reference = validate(context, canonicalProductKey, offerKey);
        Instant now = clock.instant();
        AgentProductInteraction interaction = findOrCreateForUpdate(context, reference, now);
        if (interaction.watch(reference.offerKey(), now)) {
            interactionRepository.save(interaction);
        }
        return result(interaction, reference.offerKey(), reference.label());
    }

    @Transactional
    public AgentProductInteractionResult unwatch(
            AgentToolExecutionContext context,
            String canonicalProductKey,
            String offerKey
    ) {
        AgentProductInteractionReference reference = validate(context, canonicalProductKey, offerKey);
        return interactionRepository.findForUpdate(context.userId(), reference.canonicalProductKey())
                .map(interaction -> {
                    if (interaction.unwatch(clock.instant())) {
                        interactionRepository.save(interaction);
                    }
                    return result(interaction, reference.offerKey(), reference.label());
                })
                .orElseGet(() -> inactive(reference));
    }

    @Transactional(readOnly = true)
    public List<AgentProductInteractionResult> list(AgentToolExecutionContext context, int limit) {
        requireOwnedConversation(context, false);
        return interactionRepository.findActiveByUserId(context.userId(), PageRequest.of(0, limit)).stream()
                .map(interaction -> result(interaction, preferredOffer(interaction), null))
                .toList();
    }

    private AgentProductInteractionReference validate(
            AgentToolExecutionContext context,
            String canonicalProductKey,
            String offerKey
    ) {
        requireOwnedConversation(context, true);
        return referenceService.resolve(context, canonicalProductKey, offerKey);
    }

    private AgentProductInteraction findOrCreateForUpdate(
            AgentToolExecutionContext context,
            AgentProductInteractionReference reference,
            Instant now
    ) {
        interactionRepository.insertIfAbsent(
                UUID.randomUUID(), context.userId(), reference.canonicalProductKey(), now);
        return interactionRepository.findForUpdate(context.userId(), reference.canonicalProductKey())
                .orElseThrow(() -> AgentException.conflict(
                        "The product state changed concurrently. Try again."));
    }

    private void requireOwnedConversation(AgentToolExecutionContext context, boolean lock) {
        if (context == null || context.userId() == null || context.conversationId() == null) {
            throw new IllegalArgumentException("An authenticated conversation-scoped tool context is required");
        }
        var conversation = lock
                ? conversationRepository.findOwnedForUpdate(context.conversationId(), context.userId())
                : conversationRepository.findByIdAndUserId(context.conversationId(), context.userId());
        var owned = conversation.orElseThrow(AgentException::notFound);
        if (owned.getStatus() == AgentConversationStatus.ARCHIVED) {
            throw AgentException.conflict("Archived conversations cannot change or load product interaction state.");
        }
    }

    private AgentProductInteractionResult inactive(AgentProductInteractionReference reference) {
        return new AgentProductInteractionResult(
                reference.canonicalProductKey(), reference.label(), reference.offerKey(), false, null, null,
                false, null, null, null);
    }

    private AgentProductInteractionResult result(
            AgentProductInteraction interaction,
            String offerKey,
            String label
    ) {
        return new AgentProductInteractionResult(
                interaction.getCanonicalProductKey(),
                label,
                offerKey,
                interaction.isPinned(),
                interaction.getPinnedOfferKey(),
                interaction.getPinnedAt(),
                interaction.isWatched(),
                interaction.getWatchedOfferKey(),
                interaction.getWatchedAt(),
                interaction.getUpdatedAt()
        );
    }

    private String preferredOffer(AgentProductInteraction interaction) {
        return interaction.isPinned() ? interaction.getPinnedOfferKey() : interaction.getWatchedOfferKey();
    }
}
