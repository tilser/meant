package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentMessageRole;
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
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.cart.service.CartOwnershipTransferService;
import com.meant.api.module.cart.service.CartService;
import com.meant.api.module.cart.service.command.TransferCartOwnershipCommand;
import com.meant.api.module.cart.service.dto.CartLineResult;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.cart.service.query.GetCartQuery;
import com.meant.api.module.user.service.UserCanonicalProductAccessTransferService;
import com.meant.api.module.user.service.command.TransferUserCanonicalProductAccessCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

class GuestConversationTransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    private final GuestConversationTransferRepository transferRepository =
            mock(GuestConversationTransferRepository.class);
    private final AgentConversationRepository conversationRepository = mock(AgentConversationRepository.class);
    private final AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
    private final AgentArtifactReferenceRepository artifactRepository =
            mock(AgentArtifactReferenceRepository.class);
    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final UserCanonicalProductAccessTransferService canonicalProductAccessTransferService =
            mock(UserCanonicalProductAccessTransferService.class);
    private final CartOwnershipTransferService cartOwnershipTransferService =
            mock(CartOwnershipTransferService.class);
    private final CartService cartService = mock(CartService.class);
    private final AgentArtifactService agentArtifactService = mock(AgentArtifactService.class);
    private final AgentJsonSupport jsonSupport = mock(AgentJsonSupport.class);
    private final GuestConversationTransferService service = new GuestConversationTransferService(
            transferRepository,
            conversationRepository,
            messageRepository,
            artifactRepository,
            runRepository,
            canonicalProductAccessTransferService,
            cartOwnershipTransferService,
            cartService,
            agentArtifactService,
            jsonSupport,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void issuesOnlyForTheOwnedSettledGuestConversationAndStoresAHash() {
        UUID guestUserId = UUID.randomUUID();
        AgentConversation conversation = AgentConversation.create(guestUserId, "Wool coat", NOW);
        when(conversationRepository.findOwnedForUpdate(conversation.getId(), guestUserId))
                .thenReturn(Optional.of(conversation));
        when(transferRepository.findByConversationIdForUpdate(conversation.getId()))
                .thenReturn(Optional.empty());

        var result = service.issue(new IssueGuestConversationTransferCommand(guestUserId, conversation.getId()));

        assertThat(result.token()).hasSize(43);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(GuestConversationTransferService.TOKEN_LIFETIME));
        ArgumentCaptor<GuestConversationTransfer> captor = ArgumentCaptor.forClass(GuestConversationTransfer.class);
        verify(transferRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash())
                .isEqualTo(GuestConversationTransferService.hashToken(result.token()))
                .doesNotContain(result.token());
        verify(transferRepository).lockConversationTransfer(conversation.getId());
    }

    @Test
    void claimIsBoundToTheRecordedGuestOwnerAndCannotBeReplayed() {
        String token = "one-time-token";
        UUID guestUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        AgentConversation source = AgentConversation.create(guestUserId, "Trail shoes", NOW.minusSeconds(30));
        GuestConversationTransfer transfer = GuestConversationTransfer.issue(
                GuestConversationTransferService.hashToken(token),
                guestUserId,
                source.getId(),
                NOW.plusSeconds(60),
                NOW.minusSeconds(30)
        );
        when(transferRepository.findByTokenHash(GuestConversationTransferService.hashToken(token)))
                .thenReturn(Optional.of(transfer));
        when(transferRepository.findByTokenHashForUpdate(GuestConversationTransferService.hashToken(token)))
                .thenReturn(Optional.of(transfer));
        when(conversationRepository.findOwnedForUpdate(source.getId(), guestUserId))
                .thenReturn(Optional.of(source));
        when(conversationRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AgentMessage message = AgentMessage.builder()
                .conversationId(source.getId())
                .role(AgentMessageRole.ASSISTANT)
                .contentKind(AgentContentKind.ARTIFACT)
                .sequenceNumber(1)
                .contentJson("{}")
                .createdAt(NOW.minusSeconds(20))
                .build();
        when(messageRepository.findByConversationIdOrderBySequenceNumberAsc(source.getId(), Pageable.unpaged()))
                .thenReturn(List.of(message));
        AgentArtifactReference productArtifact = AgentArtifactReference.builder()
                .conversationId(source.getId())
                .messageId(message.getId())
                .artifactType(AgentArtifactType.PRODUCT)
                .ordinal(0)
                .stableKey("product-v3_guest")
                .canonicalProductKey("product-v3_guest")
                .offerKey("offer-v2_guest")
                .payloadJson("{}")
                .createdAt(NOW.minusSeconds(20))
                .build();
        UUID cartId = UUID.randomUUID();
        UUID cartLineId = UUID.randomUUID();
        AgentArtifactReference cartArtifact = AgentArtifactReference.builder()
                .conversationId(source.getId())
                .messageId(message.getId())
                .artifactType(AgentArtifactType.CART)
                .ordinal(1)
                .stableKey("cart:" + cartId)
                .cartId(cartId)
                .payloadJson("{\"cartId\":\"" + cartId + "\"}")
                .createdAt(NOW.minusSeconds(19))
                .build();
        AgentArtifactReference cartLineArtifact = AgentArtifactReference.builder()
                .conversationId(source.getId())
                .messageId(message.getId())
                .artifactType(AgentArtifactType.CART_LINE)
                .ordinal(2)
                .stableKey("cart-line:" + cartLineId)
                .canonicalProductKey("product-v3_guest")
                .offerKey("offer-v2_guest")
                .cartId(cartId)
                .cartLineId(cartLineId)
                .payloadJson("{\"cartLineId\":\"" + cartLineId + "\"}")
                .createdAt(NOW.minusSeconds(18))
                .build();
        when(artifactRepository.findByConversationIdOrderByCreatedAtAscOrdinalAsc(source.getId()))
                .thenReturn(List.of(productArtifact, cartArtifact, cartLineArtifact));
        when(cartOwnershipTransferService.transfer(new TransferCartOwnershipCommand(guestUserId, targetUserId)))
                .thenReturn(List.of(cartId));

        var imported = service.claim(new ClaimGuestConversationTransferCommand(targetUserId, token));

        assertThat(imported.conversationId()).isNotEqualTo(source.getId());
        assertThat(transfer.getTargetUserId()).isEqualTo(targetUserId);
        assertThat(transfer.getConsumedAt()).isEqualTo(NOW);
        verify(conversationRepository).findOwnedForUpdate(source.getId(), guestUserId);
        ArgumentCaptor<TransferUserCanonicalProductAccessCommand> productAccessCaptor =
                ArgumentCaptor.forClass(TransferUserCanonicalProductAccessCommand.class);
        verify(canonicalProductAccessTransferService).transfer(productAccessCaptor.capture());
        assertThat(productAccessCaptor.getValue().sourceUserId()).isEqualTo(guestUserId);
        assertThat(productAccessCaptor.getValue().targetUserId()).isEqualTo(targetUserId);
        assertThat(productAccessCaptor.getValue().canonicalProductKeys())
                .containsExactly("product-v3_guest");
        verify(cartOwnershipTransferService).transfer(new TransferCartOwnershipCommand(
                guestUserId, targetUserId));
        verify(artifactRepository).saveAll(org.mockito.ArgumentMatchers.argThat(artifacts -> {
            assertThat(artifacts)
                    .filteredOn(artifact -> artifact.getArtifactType() == AgentArtifactType.CART)
                    .singleElement()
                    .satisfies(artifact -> assertThat(artifact.getCartId()).isEqualTo(cartId));
            assertThat(artifacts)
                    .filteredOn(artifact -> artifact.getArtifactType() == AgentArtifactType.CART_LINE)
                    .singleElement()
                    .satisfies(artifact -> {
                        assertThat(artifact.getCartId()).isEqualTo(cartId);
                        assertThat(artifact.getCartLineId()).isEqualTo(cartLineId);
                    });
            return true;
        }));
        assertThatThrownBy(() -> service.claim(new ClaimGuestConversationTransferCommand(targetUserId, token)))
                .isInstanceOfSatisfying(AgentException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.GONE));
    }

    @Test
    void claimPersistsAChatSnapshotForATransferredCartThatOnlyExistedInFrontendState() {
        String token = "local-cart-token";
        UUID guestUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID cartLineId = UUID.randomUUID();
        AgentConversation source = AgentConversation.create(guestUserId, "Coffee", NOW.minusSeconds(30));
        GuestConversationTransfer transfer = GuestConversationTransfer.issue(
                GuestConversationTransferService.hashToken(token),
                guestUserId,
                source.getId(),
                NOW.plusSeconds(60),
                NOW.minusSeconds(30)
        );
        when(transferRepository.findByTokenHash(GuestConversationTransferService.hashToken(token)))
                .thenReturn(Optional.of(transfer));
        when(transferRepository.findByTokenHashForUpdate(GuestConversationTransferService.hashToken(token)))
                .thenReturn(Optional.of(transfer));
        when(conversationRepository.findOwnedForUpdate(source.getId(), guestUserId))
                .thenReturn(Optional.of(source));
        when(conversationRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findByConversationIdOrderBySequenceNumberAsc(source.getId(), Pageable.unpaged()))
                .thenReturn(List.of());
        when(messageRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(artifactRepository.findByConversationIdOrderByCreatedAtAscOrdinalAsc(source.getId()))
                .thenReturn(List.of());
        when(cartOwnershipTransferService.transfer(new TransferCartOwnershipCommand(guestUserId, targetUserId)))
                .thenReturn(List.of(cartId));

        CartLineResult line = mock(CartLineResult.class);
        when(line.cartLineId()).thenReturn(cartLineId);
        when(line.productTitle()).thenReturn("Anonymous coffee");
        when(line.canonicalProductKey()).thenReturn("coffee-product");
        when(line.offerKey()).thenReturn("coffee-offer");
        CartResult cart = mock(CartResult.class);
        when(cart.cartId()).thenReturn(cartId);
        when(cart.lines()).thenReturn(List.of(line));
        when(cartService.get(new GetCartQuery(cartId, targetUserId, false))).thenReturn(cart);
        when(jsonSupport.writeArtifact(any())).thenReturn("{}");

        var imported = service.claim(new ClaimGuestConversationTransferCommand(targetUserId, token));

        verify(cartService).get(new GetCartQuery(cartId, targetUserId, false));
        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageRepository).saveAndFlush(messageCaptor.capture());
        AgentMessage cartMessage = messageCaptor.getValue();
        assertThat(cartMessage.getConversationId()).isEqualTo(imported.conversationId());
        assertThat(cartMessage.getRole()).isEqualTo(AgentMessageRole.TOOL);
        assertThat(cartMessage.getContentKind()).isEqualTo(AgentContentKind.TOOL_RESULT);
        assertThat(cartMessage.getCorrelationId()).isEqualTo("guest-transfer:get_active_carts");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AgentArtifact>> artifactsCaptor = ArgumentCaptor.forClass(List.class);
        verify(agentArtifactService).persist(
                eq(imported.conversationId()),
                isNull(),
                eq(cartMessage.getId()),
                isNull(),
                artifactsCaptor.capture()
        );
        assertThat(artifactsCaptor.getValue()).extracting(AgentArtifact::type)
                .containsExactly(AgentArtifactType.CART, AgentArtifactType.CART_LINE);
        assertThat(artifactsCaptor.getValue().getFirst().cartId()).isEqualTo(cartId);
        assertThat(artifactsCaptor.getValue().getLast().cartLineId()).isEqualTo(cartLineId);
    }

    @Test
    void expiredCapabilitiesFailWithoutCopyingTheConversation() {
        String token = "expired-token";
        GuestConversationTransfer transfer = GuestConversationTransfer.issue(
                GuestConversationTransferService.hashToken(token),
                UUID.randomUUID(),
                UUID.randomUUID(),
                NOW,
                NOW.minusSeconds(60)
        );
        when(transferRepository.findByTokenHash(GuestConversationTransferService.hashToken(token)))
                .thenReturn(Optional.of(transfer));
        when(transferRepository.findByTokenHashForUpdate(GuestConversationTransferService.hashToken(token)))
                .thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.claim(
                new ClaimGuestConversationTransferCommand(UUID.randomUUID(), token)))
                .isInstanceOf(AgentException.class);
    }
}
