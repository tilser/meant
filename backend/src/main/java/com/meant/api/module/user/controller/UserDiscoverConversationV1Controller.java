package com.meant.api.module.user.controller;

import com.meant.api.module.user.controller.response.UserDiscoverConversationResponse;
import com.meant.api.module.user.service.UserDiscoverConversationService;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import com.meant.api.module.user.service.query.GetUserDiscoverConversationQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Users", description = "Profile endpoints for the authenticated Supabase user")
public class UserDiscoverConversationV1Controller {

    private final UserDiscoverConversationService userDiscoverConversationService;

    @GetMapping("/me/discover/conversations/{conversationId}")
    @Operation(
            summary = "Get a Discover chat conversation",
            description = "Returns one persisted Discover chat snapshot owned by the authenticated user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Persisted Discover chat snapshot",
            content = @Content(schema = @Schema(implementation = UserDiscoverConversationResponse.class))
    )
    public UserDiscoverConversationResponse discoverConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserDiscoverConversationResponse.from(userDiscoverConversationService.get(
                new GetUserDiscoverConversationQuery(authenticatedUser.id(), conversationId)));
    }
}
