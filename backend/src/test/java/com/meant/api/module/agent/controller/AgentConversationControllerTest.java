package com.meant.api.module.agent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentConversationStatus;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.constant.AgentShelfItemKind;
import com.meant.api.module.agent.controller.request.AgentShelfContextRequest;
import com.meant.api.module.agent.controller.request.AgentShelfItemRequest;
import com.meant.api.module.agent.controller.request.AgentUserActionRequest;
import com.meant.api.module.agent.controller.request.AgentVisibleProductContextRequest;
import com.meant.api.module.agent.controller.request.CreateAgentConversationRequest;
import com.meant.api.module.agent.controller.request.SubmitAgentTurnRequest;
import com.meant.api.module.agent.service.AgentConversationService;
import com.meant.api.module.agent.service.AgentRunCoordinator;
import com.meant.api.module.agent.service.AgentTurnService;
import com.meant.api.module.agent.service.AgentUserActionService;
import com.meant.api.module.agent.service.command.DeleteAgentConversationCommand;
import com.meant.api.module.agent.service.command.CreateAgentConversationCommand;
import com.meant.api.module.agent.service.command.RecordAgentUserActionCommand;
import com.meant.api.module.agent.service.command.SubmitAgentTurnCommand;
import com.meant.api.module.agent.service.dto.AgentConversationResult;
import com.meant.api.module.agent.service.dto.AgentConversationSummaryResult;
import com.meant.api.module.agent.service.dto.AgentMessageResult;
import com.meant.api.module.agent.service.dto.SubmitAgentTurnResult;
import com.meant.api.module.agent.service.dto.AgentUserActionResult;
import com.meant.api.module.agent.service.query.GetAgentConversationQuery;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;

class AgentConversationControllerTest {

    @Test
    void createsAConversationWithTheSelectedMerchantScope() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        Instant now = Instant.now();
        AgentConversationService conversationService = mock(AgentConversationService.class);
        when(conversationService.create(any())).thenReturn(new AgentConversationSummaryResult(
                conversationId,
                "Trail shoes",
                AgentConversationStatus.ACTIVE,
                merchantId,
                null,
                0L,
                now,
                now
        ));
        AgentConversationController controller = new AgentConversationController(
                conversationService,
                mock(AgentTurnService.class),
                mock(AgentRunCoordinator.class),
                mock(AgentUserActionService.class)
        );

        var response = controller.create(
                jwt(userId),
                new CreateAgentConversationRequest("Trail shoes", merchantId)
        );

        ArgumentCaptor<CreateAgentConversationCommand> commandCaptor =
                ArgumentCaptor.forClass(CreateAgentConversationCommand.class);
        verify(conversationService).create(commandCaptor.capture());
        assertThat(commandCaptor.getValue().userId()).isEqualTo(userId);
        assertThat(commandCaptor.getValue().merchantId()).isEqualTo(merchantId);
        assertThat(response.merchantId()).isEqualTo(merchantId);
    }

    @Test
    void returnsTheCurrentRunIdAndLatestCursorInConversationDetails() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID currentRunId = UUID.randomUUID();
        Instant now = Instant.now();
        AgentConversationService conversationService = mock(AgentConversationService.class);
        when(conversationService.get(any())).thenReturn(new AgentConversationResult(
                conversationId,
                "Trail shoes",
                AgentConversationStatus.ACTIVE,
                null,
                0,
                null,
                null,
                4L,
                currentRunId,
                9L,
                List.of(),
                List.of(),
                now,
                now
        ));
        AgentConversationController controller = new AgentConversationController(
                conversationService,
                mock(AgentTurnService.class),
                mock(AgentRunCoordinator.class),
                mock(AgentUserActionService.class)
        );

        var response = controller.get(jwt(userId), conversationId, 0L, 100);

        ArgumentCaptor<GetAgentConversationQuery> queryCaptor =
                ArgumentCaptor.forClass(GetAgentConversationQuery.class);
        verify(conversationService).get(queryCaptor.capture());
        assertThat(queryCaptor.getValue().userId()).isEqualTo(userId);
        assertThat(queryCaptor.getValue().conversationId()).isEqualTo(conversationId);
        assertThat(response.currentRunId()).isEqualTo(currentRunId);
        assertThat(response.latestCursor()).isEqualTo(9L);
    }

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
        UUID productMessageId = UUID.randomUUID();
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
                new SubmitAgentTurnRequest(
                        "Add this to my cart",
                        "client-turn-1",
                        new AgentVisibleProductContextRequest(
                                productMessageId,
                                List.of("product-5", "product-6", "product-7", "product-8")
                        ),
                        new AgentShelfContextRequest(List.of(new AgentShelfItemRequest(
                                AgentShelfItemKind.PRODUCT,
                                "product-5",
                                "Linen shirt",
                                "Meant · Clothing",
                                List.of()
                        )))
                ),
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
        assertThat(turnCommand.getValue().visibleProductContext().sourceMessageId()).isEqualTo(productMessageId);
        assertThat(turnCommand.getValue().visibleProductContext().orderedCanonicalProductKeys())
                .containsExactly("product-5", "product-6", "product-7", "product-8");
        assertThat(turnCommand.getValue().shelfContext().items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.kind()).isEqualTo(AgentShelfItemKind.PRODUCT);
                    assertThat(item.canonicalProductKey()).isEqualTo("product-5");
                    assertThat(item.title()).isEqualTo("Linen shirt");
                });
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
