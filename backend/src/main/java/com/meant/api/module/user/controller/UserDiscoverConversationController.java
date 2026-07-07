package com.meant.api.module.user.controller;

import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.controller.request.UserDiscoverConversationRequest;
import com.meant.api.module.user.controller.response.UserDiscoverConversationResponse;
import com.meant.api.module.user.service.UserDiscoverConversationService;
import com.meant.api.module.user.service.command.DeleteUserDiscoverConversationCommand;
import com.meant.api.module.user.service.command.SaveUserDiscoverConversationCommand;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import com.meant.api.module.user.service.query.ListUserDiscoverConversationsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Users", description = "Profile endpoints for the authenticated Supabase user")
public class UserDiscoverConversationController {

    private static final int MAX_CONVERSATION_LIMIT = 50;

    private final UserDiscoverConversationService userDiscoverConversationService;

    @GetMapping("/me/discover/conversations")
    @Operation(
            summary = "List Discover chat conversations",
            description = "Returns persisted Discover chat snapshots for the authenticated user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Persisted Discover chat snapshots",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = UserDiscoverConversationResponse.class)
            ))
    )
    public List<UserDiscoverConversationResponse> discoverConversations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "50") int limit
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return userDiscoverConversationService.list(
                        UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                        new ListUserDiscoverConversationsQuery(authenticatedUser.id(), clampConversationLimit(limit)))
                .stream()
                .map(UserDiscoverConversationResponse::from)
                .toList();
    }

    @PutMapping("/me/discover/conversations/{conversationId}")
    @Operation(
            summary = "Save a Discover chat conversation",
            description = "Creates or replaces the persisted Discover chat snapshot for the authenticated user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Persisted Discover chat snapshot",
            content = @Content(schema = @Schema(implementation = UserDiscoverConversationResponse.class))
    )
    public UserDiscoverConversationResponse saveDiscoverConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @Valid @RequestBody UserDiscoverConversationRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserDiscoverConversationResponse.from(userDiscoverConversationService.save(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new SaveUserDiscoverConversationCommand(
                        authenticatedUser.id(),
                        conversationId,
                        request.title(),
                        request.threadJson()
                )));
    }

    @DeleteMapping("/me/discover/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete a Discover chat conversation",
            description = "Permanently deletes a persisted Discover chat snapshot for the authenticated user."
    )
    @ApiResponse(responseCode = "204", description = "Discover chat deleted")
    public void deleteDiscoverConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        userDiscoverConversationService.delete(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new DeleteUserDiscoverConversationCommand(authenticatedUser.id(), conversationId));
    }

    private int clampConversationLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_CONVERSATION_LIMIT));
    }
}
