package com.meant.api.module.user.controller;

import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.controller.request.RecordUserTasteBehaviorRequest;
import com.meant.api.module.user.controller.request.UpdateUserTasteSignalRequest;
import com.meant.api.module.user.controller.response.UserSettingsResponse;
import com.meant.api.module.user.controller.response.UserTasteProfileResponse;
import com.meant.api.module.user.controller.response.UserTasteSignalResponse;
import com.meant.api.module.user.service.UserProductSearchPreferenceService;
import com.meant.api.module.user.service.UserTasteProfileService;
import com.meant.api.module.user.service.command.AcceptUserTasteSuggestionCommand;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import com.meant.api.module.user.service.query.GetUserTasteProfileQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Users", description = "Profile endpoints for the authenticated Supabase user")
public class UserTasteProfileController {

    private final UserTasteProfileService userTasteProfileService;
    private final UserProductSearchPreferenceService userProductSearchPreferenceService;

    @GetMapping("/me/taste-profile")
    @Operation(
            summary = "Get learned taste profile",
            description = "Returns behavioral taste signals learned from saves, purchases, dismissals, and repeat searches."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Learned taste profile for the current user",
            content = @Content(schema = @Schema(implementation = UserTasteProfileResponse.class))
    )
    public UserTasteProfileResponse tasteProfile(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserTasteProfileResponse.from(userTasteProfileService.get(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new GetUserTasteProfileQuery(authenticatedUser.id())));
    }

    @PostMapping("/me/taste-profile/behaviors")
    @Operation(
            summary = "Record product taste behavior",
            description = "Records a purchase or dismissal product snapshot so it can influence future ranking."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated learned taste profile",
            content = @Content(schema = @Schema(implementation = UserTasteProfileResponse.class))
    )
    public UserTasteProfileResponse recordTasteBehavior(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RecordUserTasteBehaviorRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserTasteProfileResponse.from(userTasteProfileService.recordBehavior(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                UserCommandMapper.toRecordUserTasteBehaviorCommand(authenticatedUser.id(), request)));
    }

    @PatchMapping("/me/taste-profile/signals/{signalId}")
    @Operation(
            summary = "Edit a learned taste signal",
            description = "Updates the learned signal weight or disables/enables the signal for future ranking."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated learned taste signal",
            content = @Content(schema = @Schema(implementation = UserTasteSignalResponse.class))
    )
    public UserTasteSignalResponse updateTasteSignal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID signalId,
            @Valid @RequestBody UpdateUserTasteSignalRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserTasteSignalResponse.from(userTasteProfileService.updateSignal(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                UserCommandMapper.toUpdateUserTasteSignalCommand(authenticatedUser.id(), signalId, request)));
    }

    @DeleteMapping("/me/taste-profile/signals/{signalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a learned taste signal")
    @ApiResponse(responseCode = "204", description = "Learned taste signal removed")
    public void removeTasteSignal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID signalId
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        userTasteProfileService.removeSignal(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                authenticatedUser.id(),
                signalId);
    }

    @PostMapping("/me/taste-profile/suggestions/{filterId}:accept")
    @Operation(
            summary = "Accept a learned filter suggestion",
            description = "Adds the suggested explicit filter to current settings in one tap."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated user settings",
            content = @Content(schema = @Schema(implementation = UserSettingsResponse.class))
    )
    public UserSettingsResponse acceptTasteSuggestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String filterId
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserSettingsResponse.from(
                userTasteProfileService.acceptSuggestion(
                        UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                        new AcceptUserTasteSuggestionCommand(authenticatedUser.id(), filterId)),
                userProductSearchPreferenceService.list(authenticatedUser.id()));
    }

    @PostMapping("/me/taste-profile/suggestions/{filterId}:reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reject a learned filter suggestion")
    @ApiResponse(responseCode = "204", description = "Learned filter suggestion rejected")
    public void rejectTasteSuggestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String filterId
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        userTasteProfileService.rejectSuggestion(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new AcceptUserTasteSuggestionCommand(authenticatedUser.id(), filterId));
    }
}
