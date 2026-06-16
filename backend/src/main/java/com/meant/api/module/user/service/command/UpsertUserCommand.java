package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Creates the local profile row for an authenticated user on first sight, or refreshes its email on
 * subsequent calls. Profile names are only applied when the row is first created so they do not
 * overwrite edits the user later makes via {@code PATCH /api/users/me}.
 */
public record UpsertUserCommand(
        @NotNull UUID id,
        @NotNull @Email String email,
        String firstName,
        String surname
) {
}
