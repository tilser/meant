package com.meant.api.module.user.controller.mapper;

import com.meant.api.module.user.controller.request.UpdateUserProfileRequest;
import com.meant.api.module.user.service.command.UpdateUserProfileCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
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
}
