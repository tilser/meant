package com.meant.api.module.agent.controller;

import com.meant.api.module.agent.controller.request.AgentUserActionRequest;
import com.meant.api.module.agent.controller.request.CreateAgentConversationRequest;
import com.meant.api.module.agent.controller.request.SubmitAgentTurnRequest;
import com.meant.api.module.agent.controller.request.UpdateAgentConversationRequest;
import com.meant.api.module.agent.controller.response.AgentConversationResponse;
import com.meant.api.module.agent.controller.response.AgentConversationSummaryResponse;
import com.meant.api.module.agent.controller.response.AgentUserActionResponse;
import com.meant.api.module.agent.controller.response.SubmitAgentTurnResponse;
import com.meant.api.module.agent.service.AgentConversationService;
import com.meant.api.module.agent.service.AgentRunCoordinator;
import com.meant.api.module.agent.service.AgentTurnService;
import com.meant.api.module.agent.service.AgentUserActionService;
import com.meant.api.module.agent.service.command.CreateAgentConversationCommand;
import com.meant.api.module.agent.service.command.DeleteAgentConversationCommand;
import com.meant.api.module.agent.service.command.RecordAgentUserActionCommand;
import com.meant.api.module.agent.service.command.ShelfContextCommand;
import com.meant.api.module.agent.service.command.ShelfItemCommand;
import com.meant.api.module.agent.service.command.SubmitAgentTurnCommand;
import com.meant.api.module.agent.service.command.UpdateAgentConversationCommand;
import com.meant.api.module.agent.service.command.VisibleProductContextCommand;
import com.meant.api.module.agent.service.query.GetAgentConversationQuery;
import com.meant.api.module.agent.service.query.ListAgentConversationsQuery;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/agent/conversations")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Commerce agent", description = "Durable conversations and controlled commerce-agent turns")
public class AgentConversationController {

    private static final int DEFAULT_LIST_LIMIT = 30;
    private static final int DEFAULT_MESSAGE_LIMIT = 100;

    private final AgentConversationService conversationService;
    private final AgentTurnService turnService;
    private final AgentRunCoordinator runCoordinator;
    private final AgentUserActionService userActionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an agent conversation")
    @ApiResponse(
            responseCode = "201",
            description = "Conversation created",
            content = @Content(schema = @Schema(implementation = AgentConversationSummaryResponse.class))
    )
    public AgentConversationSummaryResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAgentConversationRequest request
    ) {
        UUID userId = AuthenticatedUser.fromJwt(jwt).id();
        return AgentConversationSummaryResponse.from(
                conversationService.create(new CreateAgentConversationCommand(
                        userId,
                        request.title(),
                        request.merchantId()
                ))
        );
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's agent conversations")
    @ApiResponse(
            responseCode = "200",
            description = "Conversation summaries",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = AgentConversationSummaryResponse.class)
            ))
    )
    public List<AgentConversationSummaryResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = "30") int limit
    ) {
        UUID userId = AuthenticatedUser.fromJwt(jwt).id();
        int requestedLimit = limit == 0 ? DEFAULT_LIST_LIMIT : limit;
        return conversationService.list(new ListAgentConversationsQuery(userId, archived, requestedLimit)).stream()
                .map(AgentConversationSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Load a conversation snapshot")
    public AgentConversationResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") long afterSequence,
            @RequestParam(defaultValue = "100") int limit
    ) {
        UUID userId = AuthenticatedUser.fromJwt(jwt).id();
        int requestedLimit = limit == 0 ? DEFAULT_MESSAGE_LIMIT : limit;
        return AgentConversationResponse.from(conversationService.get(
                new GetAgentConversationQuery(userId, conversationId, afterSequence, requestedLimit)
        ));
    }

    @PatchMapping("/{conversationId}")
    @Operation(summary = "Rename or archive an agent conversation")
    public AgentConversationSummaryResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @Valid @RequestBody UpdateAgentConversationRequest request
    ) {
        UUID userId = AuthenticatedUser.fromJwt(jwt).id();
        return AgentConversationSummaryResponse.from(conversationService.update(
                new UpdateAgentConversationCommand(userId, conversationId, request.title(), request.archived())
        ));
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an agent conversation permanently")
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId
    ) {
        UUID userId = AuthenticatedUser.fromJwt(jwt).id();
        conversationService.delete(new DeleteAgentConversationCommand(userId, conversationId));
    }

    @PostMapping("/{conversationId}/turns")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Queue an agent turn",
            description = "Cancels the current run when necessary and queues this turn behind it."
    )
    public SubmitAgentTurnResponse submitTurn(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SubmitAgentTurnRequest request,
            HttpServletRequest httpRequest
    ) {
        UUID userId = AuthenticatedUser.fromJwt(jwt).id();
        var accepted = turnService.submit(new SubmitAgentTurnCommand(
                userId,
                conversationId,
                request.message(),
                request.clientTurnId(),
                request.visibleProductContext() == null
                        ? null
                        : new VisibleProductContextCommand(
                                request.visibleProductContext().sourceMessageId(),
                                request.visibleProductContext().orderedCanonicalProductKeys()
                        ),
                request.shelfContext() == null
                        ? null
                        : new ShelfContextCommand(request.shelfContext().items().stream()
                                .map(item -> new ShelfItemCommand(
                                        item.kind(),
                                        item.canonicalProductKey(),
                                        item.title(),
                                        item.text(),
                                        item.relatedProductNames()
                                ))
                                .toList()),
                httpRequest.getRemoteAddr()
        ));
        runCoordinator.schedule(accepted.runId());
        return SubmitAgentTurnResponse.from(accepted);
    }

    @PostMapping("/{conversationId}/actions")
    @Operation(
            summary = "Execute and persist a direct product action",
            description = "Runs the registered typed capability without a model round trip and persists USER_ACTION."
    )
    public AgentUserActionResponse performAction(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @Valid @RequestBody AgentUserActionRequest request,
            HttpServletRequest httpRequest
    ) {
        UUID userId = AuthenticatedUser.fromJwt(jwt).id();
        return AgentUserActionResponse.from(userActionService.perform(new RecordAgentUserActionCommand(
                userId,
                conversationId,
                request.toolName(),
                request.argumentsJson(),
                request.idempotencyKey(),
                request.summary(),
                httpRequest.getRemoteAddr()
        )));
    }
}
