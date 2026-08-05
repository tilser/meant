package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Authenticated Supabase identity used to ensure the local profile row exists. Profile names are
 * only applied when the row is first created so they do not overwrite edits the user later makes via
 * {@code PATCH /api/users/me}.
 */
public record EnsureUserProfileCommand(
        @NotNull UUID id,
        @Email String email,
        String firstName,
        String surname
) {

    public EnsureUserProfileCommand {
        email = email == null || email.isBlank() ? null : email.trim();
    }
}
