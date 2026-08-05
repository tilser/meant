package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.GuestConversationTransfer;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.repository.GuestConversationTransferRepository;
import com.meant.api.module.agent.service.command.ClaimGuestConversationTransferCommand;
import com.meant.api.module.agent.service.command.IssueGuestConversationTransferCommand;
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
    private final GuestConversationTransferService service = new GuestConversationTransferService(
            transferRepository,
            conversationRepository,
            messageRepository,
            artifactRepository,
            runRepository,
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
        when(messageRepository.findByConversationIdOrderBySequenceNumberAsc(source.getId(), Pageable.unpaged()))
                .thenReturn(List.of());
        when(artifactRepository.findByConversationIdOrderByCreatedAtAscOrdinalAsc(source.getId()))
                .thenReturn(List.of());

        var imported = service.claim(new ClaimGuestConversationTransferCommand(targetUserId, token));

        assertThat(imported.conversationId()).isNotEqualTo(source.getId());
        assertThat(transfer.getTargetUserId()).isEqualTo(targetUserId);
        assertThat(transfer.getConsumedAt()).isEqualTo(NOW);
        verify(conversationRepository).findOwnedForUpdate(source.getId(), guestUserId);
        assertThatThrownBy(() -> service.claim(new ClaimGuestConversationTransferCommand(targetUserId, token)))
                .isInstanceOfSatisfying(AgentException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.GONE));
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
