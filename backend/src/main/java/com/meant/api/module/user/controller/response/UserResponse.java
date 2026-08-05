package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Authenticated user profile.")
public record UserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String email,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String firstName,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String surname,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        String profilePicturePath,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean newsletter,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether this profile belongs to a temporary anonymous identity.")
        boolean anonymous
) {

    public static UserResponse from(User user, boolean anonymous) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getSurname(),
                user.getProfilePicturePath(),
                user.isNewsletter(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                anonymous);
    }
}
