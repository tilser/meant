package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.entity.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String surname,
        String profilePicturePath,
        Instant createdAt,
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
