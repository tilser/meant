package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentMessageLedgerServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-19T08:00:00Z");

    @Test
    void toolResultLocksConversationBeforeLoadingAndValidatingTheRun() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID executionOwner = UUID.randomUUID();
        AgentRun run = mock(AgentRun.class);
        AgentConversation conversation = mock(AgentConversation.class);
        AgentConversationRepository conversations = mock(AgentConversationRepository.class);
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunService runService = mock(AgentRunService.class);
        when(run.getConversationId()).thenReturn(conversationId);
        when(run.getUserId()).thenReturn(userId);
        when(run.getId()).thenReturn(runId);
        when(conversation.getId()).thenReturn(conversationId);
        when(conversation.nextSequence(NOW)).thenReturn(1L);
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        when(conversations.findOwnedForUpdateByRunId(runId)).thenReturn(Optional.of(conversation));
        when(messages.save(any(AgentMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AgentMessageLedgerService service = new AgentMessageLedgerService(
                conversations,
                messages,
                runs,
                runService,
                mock(AgentConversationMemoryService.class),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        service.appendToolResult(runId, executionOwner, "call-1", "prepare_carts", "{\"ok\":true}");

        var ordered = inOrder(runs, conversations, runService, messages);
        ordered.verify(conversations).findOwnedForUpdateByRunId(runId);
        ordered.verify(runService).requireOwnedExecution(runId, executionOwner);
        ordered.verify(runs).findById(runId);
        ordered.verify(messages).save(any(AgentMessage.class));
    }

    @Test
    void terminalClarificationPersistsStructuredContextBeforeWaitingForTheUser() {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID executionOwner = UUID.randomUUID();
        AgentRun run = mock(AgentRun.class);
        AgentConversation conversation = mock(AgentConversation.class);
        AgentConversationRepository conversations = mock(AgentConversationRepository.class);
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunService runService = mock(AgentRunService.class);
        AgentConversationMemoryService memoryService = mock(AgentConversationMemoryService.class);
        when(run.getConversationId()).thenReturn(conversationId);
        when(run.getUserId()).thenReturn(userId);
        when(run.getId()).thenReturn(runId);
        when(conversation.getId()).thenReturn(conversationId);
        when(conversation.nextSequence(NOW)).thenReturn(3L);
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        when(runs.findForUpdate(runId)).thenReturn(Optional.of(run));
        when(conversations.findOwnedForUpdateByRunId(runId)).thenReturn(Optional.of(conversation));
        when(messages.save(any(AgentMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AgentMessageLedgerService service = new AgentMessageLedgerService(
                conversations,
                messages,
                runs,
                runService,
                memoryService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        String contextJson = "{\"pendingProductClarification\":{\"toolName\":\"prepare_carts\"}}";

        service.appendTerminalAssistant(
                runId,
                executionOwner,
                "Which hat should I add?",
                contextJson,
                true
        );

        ArgumentCaptor<AgentMessage> saved = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().getContentJson()).isEqualTo(contextJson);
        verify(runService).waitForUser(runId, executionOwner, "Which hat should I add?");
        verify(memoryService).refreshForRun(runId);
    }
}
