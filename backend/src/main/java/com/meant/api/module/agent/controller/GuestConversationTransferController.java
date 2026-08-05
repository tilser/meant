package com.meant.api.module.agent.controller;

import static com.meant.api.module.user.service.PermanentAccountPolicy.requirePermanentAccount;

import com.meant.api.common.security.PermanentAccountRequired;
import com.meant.api.module.agent.controller.request.ClaimGuestConversationTransferRequest;
import com.meant.api.module.agent.controller.request.IssueGuestConversationTransferRequest;
import com.meant.api.module.agent.controller.response.AgentConversationSummaryResponse;
import com.meant.api.module.agent.controller.response.GuestConversationTransferTokenResponse;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.service.GuestConversationTransferService;
import com.meant.api.module.agent.service.command.ClaimGuestConversationTransferCommand;
import com.meant.api.module.agent.service.command.IssueGuestConversationTransferCommand;
import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.service.UserService;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/agent/guest-conversation-transfers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Commerce agent", description = "Durable conversations and controlled commerce-agent turns")
public class GuestConversationTransferController {

    private final GuestConversationTransferService transferService;
    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Prepare the active guest conversation for an existing-account sign-in")
    @ApiResponse(
            responseCode = "201",
            description = "Transfer capability issued",
            content = @Content(schema = @Schema(implementation = GuestConversationTransferTokenResponse.class))
    )
    public GuestConversationTransferTokenResponse issue(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody IssueGuestConversationTransferRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        if (!authenticatedUser.anonymous()) {
            throw AgentException.conflict("Only an active guest conversation can be prepared for transfer.");
        }
        userService.ensureProfile(UserCommandMapper.toEnsureProfileCommand(authenticatedUser));
        return GuestConversationTransferTokenResponse.from(transferService.issue(
                new IssueGuestConversationTransferCommand(authenticatedUser.id(), request.conversationId())
        ));
    }

    @PostMapping("/claim")
    @PermanentAccountRequired
    @Operation(summary = "Import a prepared guest conversation into the permanent account")
    @ApiResponse(
            responseCode = "200",
            description = "Imported conversation",
            content = @Content(schema = @Schema(implementation = AgentConversationSummaryResponse.class))
    )
    public AgentConversationSummaryResponse claim(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ClaimGuestConversationTransferRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        requirePermanentAccount(authenticatedUser);
        userService.ensureProfile(UserCommandMapper.toEnsureProfileCommand(authenticatedUser));
        return AgentConversationSummaryResponse.from(transferService.claim(
                new ClaimGuestConversationTransferCommand(authenticatedUser.id(), request.token())
        ));
    }
}
