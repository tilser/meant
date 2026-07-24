package com.meant.api.module.user.controller;

import com.meant.api.module.location.exception.InvalidLocationException;
import com.meant.api.module.location.service.LocationService;
import com.meant.api.module.location.service.dto.LocationSuggestion;
import com.meant.api.module.location.service.query.ResolveLocationQuery;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.controller.request.UserLocationRequest;
import com.meant.api.module.user.controller.request.UpdateUserNewsletterRequest;
import com.meant.api.module.user.controller.request.UpdateUserProfilePictureRequest;
import com.meant.api.module.user.controller.request.UpdateUserProfileRequest;
import com.meant.api.module.user.controller.request.UpdateUserSettingsRequest;
import com.meant.api.module.user.controller.response.UserResponse;
import com.meant.api.module.user.controller.response.UserSettingsResponse;
import com.meant.api.module.user.service.UserPreferenceFilterParsingService;
import com.meant.api.module.user.service.UserProductSearchPreferenceService;
import com.meant.api.module.user.service.UserService;
import com.meant.api.module.user.service.UserSettingsService;
import com.meant.api.module.user.service.command.DeleteUserProductSearchPreferenceCommand;
import com.meant.api.module.user.service.command.ParseUserPreferenceFiltersCommand;
import com.meant.api.module.user.service.command.UserLocationCommand;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import com.meant.api.module.user.service.dto.ParsedUserPreferenceFilters;
import com.meant.api.module.user.service.dto.UserLocationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Users", description = "Profile endpoints for the authenticated Supabase user")
public class UserProfileSettingsController {

    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final UserPreferenceFilterParsingService userPreferenceFilterParsingService;
    private final UserProductSearchPreferenceService userProductSearchPreferenceService;
    private final LocationService locationService;

    @GetMapping("/me")
    @Operation(
            summary = "Get current user",
            description = "Returns the profile of the authenticated user, creating it from the Supabase "
                    + "JWT on first call."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Current user profile",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserResponse.from(userService.ensureProfile(UserCommandMapper.toEnsureProfileCommand(authenticatedUser)));
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
        // Profile provisioning and the name edit run in a single service transaction (the row may not
        // exist yet if a client PATCHes before ever calling GET /me).
        return UserResponse.from(userService.updateProfile(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                UserCommandMapper.toUpdateCommand(authenticatedUser.id(), request)));
    }

    @PatchMapping("/me/newsletter")
    @Operation(
            summary = "Update current user's newsletter subscription",
            description = "Stores whether the authenticated user wants newsletter updates."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated user profile",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    public UserResponse updateNewsletter(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserNewsletterRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserResponse.from(userService.updateNewsletter(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                UserCommandMapper.toUpdateCommand(authenticatedUser.id(), request)));
    }

    @PatchMapping("/me/profile-picture")
    @Operation(
            summary = "Update current user profile picture",
            description = "Stores the Supabase Storage object path for the authenticated user's profile picture."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated user profile",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    public UserResponse updateProfilePicture(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateUserProfilePictureRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserResponse.from(userService.updateProfilePicture(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                UserCommandMapper.toUpdateCommand(authenticatedUser.id(), request)));
    }

    @DeleteMapping("/me/profile-picture")
    @Operation(
            summary = "Remove current user profile picture",
            description = "Clears the stored profile picture object path for the authenticated user."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated user profile",
            content = @Content(schema = @Schema(implementation = UserResponse.class))
    )
    public UserResponse removeProfilePicture(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserResponse.from(userService.removeProfilePicture(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser)));
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
        return UserSettingsResponse.from(
                userSettingsService.get(UserCommandMapper.toEnsureProfileCommand(authenticatedUser)),
                userProductSearchPreferenceService.list(authenticatedUser.id()));
    }

    @PatchMapping("/me/settings")
    @Operation(
            summary = "Update current user settings",
            description = "Updates shopping settings. When preferenceDescription is present, it is parsed into "
                    + "canonical shopping filters and merged into the active filter set before saving. Stable "
                    + "product-scoped search preferences are merged by scope; omitted scopes are unchanged."
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
        List<UserLocationCommand> resolvedLocations = resolveLocations(authenticatedUser, request);
        var settings = userSettingsService.update(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                UserCommandMapper.toUpdateSettingsCommand(
                        authenticatedUser.id(), request, parsedFilters, resolvedLocations));
        if (request.productSearchPreferences() != null) {
            userProductSearchPreferenceService.upsert(
                    UserCommandMapper.toSaveProductSearchPreferencesCommand(authenticatedUser.id(), request));
        }
        return UserSettingsResponse.from(
                settings,
                userProductSearchPreferenceService.list(authenticatedUser.id()));
    }

    @DeleteMapping("/me/settings/product-search-preferences/{scope}")
    @Operation(
            operationId = "deleteProductSearchPreference",
            summary = "Delete a scoped product-search size preference",
            description = "Deletes the authenticated user's stable SIZE preference for one normalized product scope. "
                    + "Preferences for every other scope remain unchanged."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated user settings and remaining product-search preferences",
            content = @Content(schema = @Schema(implementation = UserSettingsResponse.class))
    )
    public UserSettingsResponse deleteProductSearchPreference(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(
                    description = "Product scope whose stable SIZE preference should be deleted",
                    required = true,
                    example = "footwear"
            )
            @PathVariable String scope
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        var profileCommand = UserCommandMapper.toEnsureProfileCommand(authenticatedUser);
        var settings = userSettingsService.get(profileCommand);
        userProductSearchPreferenceService.delete(new DeleteUserProductSearchPreferenceCommand(
                authenticatedUser.id(), scope, UserProductSearchAttributeName.SIZE));
        return UserSettingsResponse.from(
                settings,
                userProductSearchPreferenceService.list(authenticatedUser.id()));
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

    private List<UserLocationCommand> resolveLocations(
            AuthenticatedUser authenticatedUser,
            UpdateUserSettingsRequest request
    ) {
        List<UserLocationRequest> requestedLocations = request.locations();
        if (requestedLocations == null && request.location() != null) {
            requestedLocations = List.of(request.location());
        }
        if (requestedLocations == null) {
            return null;
        }
        Map<String, UserLocationResult> existingLocations = userSettingsService
                .get(UserCommandMapper.toEnsureProfileCommand(authenticatedUser))
                .locations()
                .stream()
                .collect(Collectors.toMap(
                        UserLocationResult::id,
                        Function.identity(),
                        (first, ignored) -> first
                ));
        return requestedLocations.stream()
                .map(location -> resolveLocation(location, existingLocations))
                .toList();
    }

    private UserLocationCommand resolveLocation(
            UserLocationRequest request,
            Map<String, UserLocationResult> existingLocations
    ) {
        UserLocationResult existing = existingLocations.get(request.id());
        if (existing != null) {
            return new UserLocationCommand(
                    existing.id(),
                    existing.country(),
                    existing.code(),
                    existing.region(),
                    existing.postalCode(),
                    existing.regionName(),
                    existing.city()
            );
        }
        if (request.id().startsWith("legacy:")) {
            throw new InvalidLocationException("Legacy location does not belong to this user");
        }
        LocationSuggestion location = locationService.resolve(new ResolveLocationQuery(request.id(), "en"));
        return new UserLocationCommand(
                location.id(),
                location.countryName(),
                location.country(),
                location.region(),
                location.postalCode(),
                location.regionName(),
                location.city()
        );
    }
}
