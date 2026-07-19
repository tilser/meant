package com.meant.api.module.agent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.controller.request.AgentUserActionRequest;
import com.meant.api.module.agent.controller.request.SubmitAgentTurnRequest;
import com.meant.api.module.agent.service.AgentConversationService;
import com.meant.api.module.agent.service.AgentRunCoordinator;
import com.meant.api.module.agent.service.AgentTurnService;
import com.meant.api.module.agent.service.AgentUserActionService;
import com.meant.api.module.agent.service.command.DeleteAgentConversationCommand;
import com.meant.api.module.agent.service.command.RecordAgentUserActionCommand;
import com.meant.api.module.agent.service.command.SubmitAgentTurnCommand;
import com.meant.api.module.agent.service.dto.AgentMessageResult;
import com.meant.api.module.agent.service.dto.AgentUserActionResult;
import com.meant.api.module.agent.service.dto.SubmitAgentTurnResult;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;

class AgentConversationControllerTest {

    @Test
    void deletesTheAuthenticatedUsersConversation() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AgentConversationService conversationService = mock(AgentConversationService.class);
        AgentConversationController controller = new AgentConversationController(
                conversationService,
                mock(AgentTurnService.class),
                mock(AgentRunCoordinator.class),
                mock(AgentUserActionService.class)
        );

        controller.delete(jwt(userId), conversationId);

        ArgumentCaptor<DeleteAgentConversationCommand> commandCaptor =
                ArgumentCaptor.forClass(DeleteAgentConversationCommand.class);
        verify(conversationService).delete(commandCaptor.capture());
        assertThat(commandCaptor.getValue().userId()).isEqualTo(userId);
        assertThat(commandCaptor.getValue().conversationId()).isEqualTo(conversationId);
    }

    @Test
    void forwardsTheTrustedRequestAddressToTurnsAndDirectActions() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AgentTurnService turnService = mock(AgentTurnService.class);
        AgentUserActionService userActionService = mock(AgentUserActionService.class);
        AgentRunCoordinator runCoordinator = mock(AgentRunCoordinator.class);
        AgentConversationController controller = new AgentConversationController(
                mock(AgentConversationService.class),
                turnService,
                runCoordinator,
                userActionService
        );
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.42");
        AgentMessageResult message = new AgentMessageResult(
                UUID.randomUUID(),
                runId,
                1L,
                AgentMessageRole.USER,
                AgentContentKind.TEXT,
                "Add this to my cart",
                null,
                "client-turn-1",
                Instant.now()
        );
        when(turnService.submit(any())).thenReturn(new SubmitAgentTurnResult(runId, 0L, message));
        when(userActionService.perform(any())).thenReturn(new AgentUserActionResult(message, "{}", List.of()));

        controller.submitTurn(
                jwt(userId),
                conversationId,
                new SubmitAgentTurnRequest("Add this to my cart", "client-turn-1"),
                request
        );
        controller.performAction(
                jwt(userId),
                conversationId,
                new AgentUserActionRequest(
                        "prepare_carts",
                        "{\"offers\":[{\"offerKey\":\"offer-1\"}]}",
                        "action-1",
                        "Added product to cart"
                ),
                request
        );

        ArgumentCaptor<SubmitAgentTurnCommand> turnCommand =
                ArgumentCaptor.forClass(SubmitAgentTurnCommand.class);
        ArgumentCaptor<RecordAgentUserActionCommand> actionCommand =
                ArgumentCaptor.forClass(RecordAgentUserActionCommand.class);
        verify(turnService).submit(turnCommand.capture());
        verify(userActionService).perform(actionCommand.capture());
        verify(runCoordinator).schedule(runId);
        assertThat(turnCommand.getValue().buyerIp()).isEqualTo("203.0.113.42");
        assertThat(actionCommand.getValue().buyerIp()).isEqualTo("203.0.113.42");
    }

    private Jwt jwt(UUID userId) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
