package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String surname,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String profilePicturePath,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getSurname(),
                user.getProfilePicturePath(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
