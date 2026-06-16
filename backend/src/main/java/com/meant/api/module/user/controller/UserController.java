package com.meant.api.module.user.controller;

import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.controller.request.UpdateUserProfileRequest;
import com.meant.api.module.user.controller.request.UpdateUserSettingsRequest;
import com.meant.api.module.user.controller.response.UserResponse;
import com.meant.api.module.user.controller.response.UserSettingsResponse;
import com.meant.api.module.user.service.UserPreferenceFilterParsingService;
import com.meant.api.module.user.service.UserService;
import com.meant.api.module.user.service.UserSettingsService;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import com.meant.api.module.user.service.command.ParseUserPreferenceFiltersCommand;
import com.meant.api.module.user.service.dto.ParsedUserPreferenceFilters;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Users", description = "Profile endpoints for the authenticated Supabase user")
public class UserController {

    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final UserPreferenceFilterParsingService userPreferenceFilterParsingService;

    @GetMapping("/me")
    @Operation(
            summary = "Get current user",
            description = "Returns the profile of the authenticated user, creating it from the Supabase "
                    + "JWT on first call (upsert-on-read)."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Current user profile",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserResponse.from(userService.upsert(UserCommandMapper.toUpsertCommand(authenticatedUser)));
    }

    @PatchMapping("/me")
    @Operation(
            summary = "Update current user profile",
            description = "Updates the first name and surname of the authenticated user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated user profile",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    public UserResponse updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        // Upsert-from-JWT and the name edit run in a single service transaction (the row may not exist
        // yet if a client PATCHes before ever calling GET /me).
        return UserResponse.from(userService.updateProfile(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                UserCommandMapper.toUpdateCommand(authenticatedUser.id(), request)));
    }

    @GetMapping("/me/settings")
    @Operation(
            summary = "Get current user settings",
            description = "Returns the current user's shopping settings and canonical filter catalog."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Current user settings",
            content = @Content(schema = @Schema(implementation = UserSettingsResponse.class))
    )
    public UserSettingsResponse settings(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserSettingsResponse.from(userSettingsService.get(
                UserCommandMapper.toUpsertCommand(authenticatedUser)));
    }

    @PatchMapping("/me/settings")
    @Operation(
            summary = "Update current user settings",
            description = "Updates shopping settings. When preferenceDescription is present, it is parsed into "
                    + "canonical shopping filters and merged into the active filter set before saving."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated user settings",
            content = @Content(schema = @Schema(implementation = UserSettingsResponse.class))
    )
    public UserSettingsResponse updateSettings(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserSettingsRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        ParsedUserPreferenceFilters parsedFilters = parseFilters(authenticatedUser, request);
        return UserSettingsResponse.from(userSettingsService.update(
                UserCommandMapper.toUpsertCommand(authenticatedUser),
                UserCommandMapper.toUpdateSettingsCommand(authenticatedUser.id(), request, parsedFilters)));
    }

    private ParsedUserPreferenceFilters parseFilters(
            AuthenticatedUser authenticatedUser,
            UpdateUserSettingsRequest request
    ) {
        if (request.preferenceDescription() == null || request.preferenceDescription().isBlank()) {
            return ParsedUserPreferenceFilters.empty();
        }
        return userPreferenceFilterParsingService.parse(new ParseUserPreferenceFiltersCommand(
                authenticatedUser.id(),
                request.preferenceDescription().trim()));
    }
}
