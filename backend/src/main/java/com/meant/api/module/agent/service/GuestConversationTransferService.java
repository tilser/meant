package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.entity.GuestConversationTransfer;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.repository.GuestConversationTransferRepository;
import com.meant.api.module.agent.service.command.ClaimGuestConversationTransferCommand;
import com.meant.api.module.agent.service.command.IssueGuestConversationTransferCommand;
import com.meant.api.module.agent.service.dto.AgentCartResult;
import com.meant.api.module.agent.service.dto.AgentConversationSummaryResult;
import com.meant.api.module.agent.service.dto.GuestConversationTransferToken;
import com.meant.api.module.cart.service.CartOwnershipTransferService;
import com.meant.api.module.cart.service.CartService;
import com.meant.api.module.cart.service.command.TransferCartOwnershipCommand;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.cart.service.query.GetCartQuery;
import com.meant.api.module.user.service.UserCanonicalProductAccessTransferService;
import com.meant.api.module.user.service.command.TransferUserCanonicalProductAccessCommand;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class GuestConversationTransferService {

    static final Duration TOKEN_LIFETIME = Duration.ofMinutes(10);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final EnumSet<AgentArtifactType> IMPORTABLE_ARTIFACT_TYPES = EnumSet.of(
            AgentArtifactType.PRODUCT,
            AgentArtifactType.OFFER,
            AgentArtifactType.PRODUCT_STATE,
            AgentArtifactType.COMPARISON,
            AgentArtifactType.REVIEWS,
            AgentArtifactType.DISCOUNT_CODES,
            AgentArtifactType.CART,
            AgentArtifactType.CART_LINE
    );

    private final GuestConversationTransferRepository transferRepository;
    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentArtifactReferenceRepository artifactRepository;
    private final AgentRunRepository runRepository;
    private final UserCanonicalProductAccessTransferService canonicalProductAccessTransferService;
    private final CartOwnershipTransferService cartOwnershipTransferService;
    private final CartService cartService;
    private final AgentArtifactService agentArtifactService;
    private final AgentJsonSupport jsonSupport;
    private final Clock clock;

    @Transactional
    public GuestConversationTransferToken issue(@Valid IssueGuestConversationTransferCommand command) {
        transferRepository.lockConversationTransfer(command.conversationId());
        AgentConversation conversation = conversationRepository.findOwnedForUpdate(
                        command.conversationId(),
                        command.guestUserId()
                )
                .orElseThrow(AgentException::notFound);
        requireSettled(conversation.getId());

        Instant now = clock.instant();
        Instant expiresAt = now.plus(TOKEN_LIFETIME);
        String token = newToken();
        String tokenHash = hashToken(token);
        GuestConversationTransfer transfer = transferRepository
                .findByConversationIdForUpdate(conversation.getId())
                .map(existing -> rotate(existing, tokenHash, expiresAt, now))
                .orElseGet(() -> GuestConversationTransfer.issue(
                        tokenHash,
                        command.guestUserId(),
                        conversation.getId(),
                        expiresAt,
                        now
                ));
        transferRepository.save(transfer);
        return new GuestConversationTransferToken(token, conversation.getId(), expiresAt);
    }

    @Transactional
    public AgentConversationSummaryResult claim(@Valid ClaimGuestConversationTransferCommand command) {
        Instant now = clock.instant();
        GuestConversationTransfer candidate = transferRepository.findByTokenHash(hashToken(command.token()))
                .orElseThrow(AgentException::transferUnavailable);
        transferRepository.lockConversationTransfer(candidate.getConversationId());
        GuestConversationTransfer transfer = transferRepository.findByTokenHashForUpdate(hashToken(command.token()))
                .orElseThrow(AgentException::transferUnavailable);
        if (transfer.getConsumedAt() != null || !transfer.getExpiresAt().isAfter(now)) {
            throw AgentException.transferUnavailable();
        }

        AgentConversation source = conversationRepository.findOwnedForUpdate(
                        transfer.getConversationId(),
                        transfer.getGuestUserId()
                )
                .orElseThrow(AgentException::transferUnavailable);
        requireSettled(source.getId());

        AgentConversation imported;
        if (transfer.getGuestUserId().equals(command.targetUserId())) {
            imported = source;
        } else {
            ConversationImport conversationImport = copyVisibleConversation(source, command.targetUserId(), now);
            canonicalProductAccessTransferService.transfer(new TransferUserCanonicalProductAccessCommand(
                    transfer.getGuestUserId(),
                    command.targetUserId(),
                    conversationImport.canonicalProductKeys()
            ));
            List<UUID> transferredCartIds = cartOwnershipTransferService.transfer(new TransferCartOwnershipCommand(
                    transfer.getGuestUserId(),
                    command.targetUserId()
            ));
            persistMissingTransferredCartSnapshot(
                    conversationImport,
                    command.targetUserId(),
                    transferredCartIds,
                    now
            );
            imported = conversationImport.conversation();
        }
        transfer.consume(command.targetUserId(), now);
        return AgentResultMapper.conversation(imported);
    }

    static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private GuestConversationTransfer rotate(
            GuestConversationTransfer existing,
            String tokenHash,
            Instant expiresAt,
            Instant now
    ) {
        if (existing.getConsumedAt() != null) {
            throw AgentException.transferUnavailable();
        }
        existing.rotate(tokenHash, expiresAt, now);
        return existing;
    }

    private void requireSettled(UUID conversationId) {
        if (runRepository.existsByConversationIdAndStatusIn(
                conversationId,
                List.of(AgentRunStatus.QUEUED, AgentRunStatus.RUNNING)
        )) {
            throw AgentException.conflict("Stop the current search before signing in to an existing account.");
        }
    }

    private ConversationImport copyVisibleConversation(AgentConversation source, UUID targetUserId, Instant now) {
        AgentConversation imported = conversationRepository.saveAndFlush(AgentConversation.builder()
                .userId(targetUserId)
                .merchantId(source.getMerchantId())
                .title(source.getTitle())
                .status(source.getStatus())
                .rollingSummary(source.getRollingSummary())
                .summaryVersion(source.getSummaryVersion())
                .lastSequenceNumber(source.getLastSequenceNumber())
                .createdAt(now)
                .updatedAt(now)
                .build());

        Map<UUID, UUID> messageIds = new HashMap<>();
        List<AgentMessage> messages = messageRepository
                .findByConversationIdOrderBySequenceNumberAsc(source.getId(), Pageable.unpaged())
                .stream()
                .map(message -> copyMessage(message, imported.getId(), messageIds))
                .toList();
        messageRepository.saveAllAndFlush(messages);

        List<AgentArtifactReference> artifacts = artifactRepository
                .findByConversationIdOrderByCreatedAtAscOrdinalAsc(source.getId())
                .stream()
                .filter(artifact -> IMPORTABLE_ARTIFACT_TYPES.contains(artifact.getArtifactType()))
                .filter(artifact -> artifact.getMessageId() != null && messageIds.containsKey(artifact.getMessageId()))
                .map(artifact -> copyArtifact(artifact, imported.getId(), messageIds.get(artifact.getMessageId())))
                .toList();
        artifactRepository.saveAll(artifacts);
        List<String> canonicalProductKeys = artifacts.stream()
                .map(AgentArtifactReference::getCanonicalProductKey)
                .filter(key -> key != null && !key.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        List<UUID> copiedCartIds = artifacts.stream()
                .filter(artifact -> artifact.getArtifactType() == AgentArtifactType.CART)
                .map(AgentArtifactReference::getCartId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return new ConversationImport(imported, canonicalProductKeys, copiedCartIds);
    }

    private void persistMissingTransferredCartSnapshot(
            ConversationImport conversationImport,
            UUID targetUserId,
            List<UUID> transferredCartIds,
            Instant now
    ) {
        List<CartResult> missingCarts = transferredCartIds.stream()
                .filter(cartId -> !conversationImport.copiedCartIds().contains(cartId))
                .map(cartId -> cartService.get(new GetCartQuery(cartId, targetUserId, false)))
                .toList();
        if (missingCarts.isEmpty()) {
            return;
        }

        AgentConversation imported = conversationImport.conversation();
        AgentMessage cartMessage = messageRepository.saveAndFlush(AgentMessage.builder()
                .conversationId(imported.getId())
                .role(AgentMessageRole.TOOL)
                .contentKind(AgentContentKind.TOOL_RESULT)
                .sequenceNumber(imported.nextSequence(now))
                .correlationId("guest-transfer:get_active_carts")
                .createdAt(now)
                .build());
        agentArtifactService.persist(
                imported.getId(),
                null,
                cartMessage.getId(),
                null,
                AgentCartArtifacts.from(AgentCartResult.success(missingCarts), jsonSupport)
        );
    }

    private AgentMessage copyMessage(AgentMessage source, UUID conversationId, Map<UUID, UUID> messageIds) {
        UUID messageId = UUID.randomUUID();
        messageIds.put(source.getId(), messageId);
        return AgentMessage.builder()
                .id(messageId)
                .conversationId(conversationId)
                .role(source.getRole())
                .contentKind(source.getContentKind())
                .sequenceNumber(source.getSequenceNumber())
                .textContent(source.getTextContent())
                .contentJson(source.getContentJson())
                .correlationId(source.getCorrelationId())
                .createdAt(source.getCreatedAt())
                .build();
    }

    private AgentArtifactReference copyArtifact(AgentArtifactReference source, UUID conversationId, UUID messageId) {
        return AgentArtifactReference.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .artifactType(source.getArtifactType())
                .ordinal(source.getOrdinal())
                .stableKey(source.getStableKey())
                .label(source.getLabel())
                .canonicalProductKey(source.getCanonicalProductKey())
                .offerKey(source.getOfferKey())
                .cartId(source.getCartId())
                .cartLineId(source.getCartLineId())
                .payloadJson(source.getPayloadJson())
                .createdAt(source.getCreatedAt())
                .build();
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record ConversationImport(
            AgentConversation conversation,
            List<String> canonicalProductKeys,
            List<UUID> copiedCartIds
    ) {
    }
}
