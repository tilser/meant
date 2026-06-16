package com.meant.api.module.user.controller.mapper;

import com.meant.api.module.user.controller.request.UpdateUserSettingsRequest;
import com.meant.api.module.user.controller.request.UpdateUserProfileRequest;
import com.meant.api.module.user.service.command.UpdateUserProfileCommand;
import com.meant.api.module.user.service.command.UpdateUserSettingsCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.command.UserLocationCommand;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import com.meant.api.module.user.service.dto.ParsedUserPreferenceFilters;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UserCommandMapper {

    private UserCommandMapper() {
    }

    public static UpsertUserCommand toUpsertCommand(AuthenticatedUser authenticatedUser) {
        return new UpsertUserCommand(
                authenticatedUser.id(),
                authenticatedUser.email(),
                authenticatedUser.firstName(),
                authenticatedUser.surname());
    }

    public static UpdateUserProfileCommand toUpdateCommand(UUID userId, UpdateUserProfileRequest request) {
        return new UpdateUserProfileCommand(
                userId,
                request.firstName(),
                request.surname());
    }

    public static UpdateUserSettingsCommand toUpdateSettingsCommand(
            UUID userId,
            UpdateUserSettingsRequest request,
            ParsedUserPreferenceFilters parsedFilters
    ) {
        return new UpdateUserSettingsCommand(
                userId,
                request.budget(),
                request.location() == null
                        ? null
                        : new UserLocationCommand(
                                request.location().country(),
                                request.location().code(),
                                request.location().city()),
                request.filterIds() == null ? null : new LinkedHashSet<>(request.filterIds()),
                parsedFilters == null ? Set.of() : new LinkedHashSet<>(parsedFilters.filterIds()),
                parsedFilters == null ? List.of() : parsedFilters.unmappedPreferences());
    }
}
