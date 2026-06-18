package com.meant.api.module.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

    private static User newUser(String email, String firstName, String surname) {
        Instant created = Instant.parse("2020-01-01T00:00:00Z");
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .firstName(firstName)
                .surname(surname)
                .createdAt(created)
                .updatedAt(created)
                .build();
    }

    @Test
    void updateEmailIsNoOpWhenUnchanged() {
        User user = newUser("ada@example.com", "Ada", "Lovelace");
        Instant before = user.getUpdatedAt();

        user.updateEmail("ada@example.com", Instant.parse("2025-01-01T00:00:00Z"));

        // updatedAt must not advance: this keeps upsert-on-read from flushing a redundant UPDATE.
        assertThat(user.getUpdatedAt()).isEqualTo(before);
    }

    @Test
    void updateEmailAdvancesTimestampWhenChanged() {
        User user = newUser("ada@example.com", "Ada", "Lovelace");
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        user.updateEmail("ada@new.com", now);

        assertThat(user.getEmail()).isEqualTo("ada@new.com");
        assertThat(user.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void updateProfileIsNoOpWhenNamesUnchanged() {
        User user = newUser("ada@example.com", "Ada", "Lovelace");
        Instant before = user.getUpdatedAt();

        user.updateProfile("Ada", "Lovelace", Instant.parse("2025-01-01T00:00:00Z"));

        assertThat(user.getUpdatedAt()).isEqualTo(before);
    }

    @Test
    void updateProfileAdvancesTimestampWhenNamesChange() {
        User user = newUser("ada@example.com", "Ada", "Lovelace");
        Instant now = Instant.parse("2025-01-01T00:00:00Z");

        user.updateProfile("Augusta", "Byron", now);

        assertThat(user.getFirstName()).isEqualTo("Augusta");
        assertThat(user.getSurname()).isEqualTo("Byron");
        assertThat(user.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void updateProfilePictureIsNoOpWhenPathUnchanged() {
        User user = newUser("ada@example.com", "Ada", "Lovelace");
        Instant firstUpdate = Instant.parse("2024-01-01T00:00:00Z");
        user.updateProfilePicture("%s/avatar.webp".formatted(user.getId()), firstUpdate);
        Instant before = user.getUpdatedAt();

        user.updateProfilePicture("%s/avatar.webp".formatted(user.getId()), Instant.parse("2025-01-01T00:00:00Z"));

        assertThat(user.getUpdatedAt()).isEqualTo(before);
    }

    @Test
    void updateProfilePictureAdvancesTimestampWhenPathChanges() {
        User user = newUser("ada@example.com", "Ada", "Lovelace");
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        String profilePicturePath = "%s/avatar.webp".formatted(user.getId());

        user.updateProfilePicture(profilePicturePath, now);

        assertThat(user.getProfilePicturePath()).isEqualTo(profilePicturePath);
        assertThat(user.getUpdatedAt()).isEqualTo(now);
    }
}
