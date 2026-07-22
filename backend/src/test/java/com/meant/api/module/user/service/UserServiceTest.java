package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserRepository;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.UpdateUserNewsletterCommand;
import com.meant.api.module.user.service.command.UpdateUserProfilePictureCommand;
import com.meant.api.module.user.service.command.UpdateUserProfileCommand;
import com.meant.api.module.user.service.query.GetUserQuery;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserServiceTest {

    private FakeUserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        userService = new UserService(userRepository.proxy());
    }

    @Test
    void ensureProfileCreatesUserOnFirstSight() {
        UUID id = UUID.randomUUID();

        User created = userService.ensureProfile(
                new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"));

        assertThat(created.getId()).isEqualTo(id);
        assertThat(created.getEmail()).isEqualTo("ada@example.com");
        assertThat(created.getFirstName()).isEqualTo("Ada");
        assertThat(created.getSurname()).isEqualTo("Lovelace");
        assertThat(created.isNewsletter()).isFalse();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertThat(userRepository.insertCount).isEqualTo(1);
    }

    @Test
    void ensureProfileRefreshesEmailButPreservesProfileOnSecondCall() {
        UUID id = UUID.randomUUID();
        userService.ensureProfile(new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"));

        User updated = userService.ensureProfile(
                new EnsureUserProfileCommand(id, "ada@new.com", "ShouldBeIgnored", "Ignored"));

        assertThat(updated.getEmail()).isEqualTo("ada@new.com");
        assertThat(updated.getFirstName()).isEqualTo("Ada");
        assertThat(updated.getSurname()).isEqualTo("Lovelace");
        // First call inserts; second call mutates the existing row without inserting again.
        assertThat(userRepository.insertCount).isEqualTo(1);
    }

    @Test
    void ensureProfileBackfillsANameMissingFromAHistoricalProfile() {
        UUID id = UUID.randomUUID();
        userService.ensureProfile(new EnsureUserProfileCommand(id, "ada@example.com", null, null));

        User updated = userService.ensureProfile(
                new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"));

        assertThat(updated.getFirstName()).isEqualTo("Ada");
        assertThat(updated.getSurname()).isEqualTo("Lovelace");
        assertThat(userRepository.insertCount).isEqualTo(1);
    }

    @Test
    void updateProfileMutatesNamesOnExistingUser() {
        UUID id = UUID.randomUUID();
        userService.ensureProfile(new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"));

        User updated = userService.updateProfile(
                new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"),
                new UpdateUserProfileCommand(id, "Augusta", "Byron"));

        assertThat(updated.getFirstName()).isEqualTo("Augusta");
        assertThat(updated.getSurname()).isEqualTo("Byron");
        // No second insert: the existing row is mutated in place.
        assertThat(userRepository.insertCount).isEqualTo(1);
    }

    @Test
    void updateProfileCreatesUserWhenPatchedBeforeFirstRead() {
        UUID id = UUID.randomUUID();

        User created = userService.updateProfile(
                new EnsureUserProfileCommand(id, "grace@example.com", "Grace", "Hopper"),
                new UpdateUserProfileCommand(id, "Grace", "Murray Hopper"));

        assertThat(created.getId()).isEqualTo(id);
        assertThat(created.getEmail()).isEqualTo("grace@example.com");
        assertThat(created.getSurname()).isEqualTo("Murray Hopper");
        assertThat(userRepository.insertCount).isEqualTo(1);
    }

    @Test
    void updateNewsletterMutatesSubscriptionFlag() {
        UUID id = UUID.randomUUID();
        userService.ensureProfile(new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"));

        User subscribed = userService.updateNewsletter(
                new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"),
                new UpdateUserNewsletterCommand(id, true));

        assertThat(subscribed.isNewsletter()).isTrue();

        User unsubscribed = userService.updateNewsletter(
                new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"),
                new UpdateUserNewsletterCommand(id, false));

        assertThat(unsubscribed.isNewsletter()).isFalse();
        assertThat(userRepository.usersById.get(id).isNewsletter()).isFalse();
        assertThat(userRepository.insertCount).isEqualTo(1);
    }

    @Test
    void updateProfilePictureStoresOwnedObjectPath() {
        UUID id = UUID.randomUUID();
        userService.ensureProfile(new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"));
        String profilePicturePath = id + "/avatar.webp";

        User updated = userService.updateProfilePicture(
                new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"),
                new UpdateUserProfilePictureCommand(id, profilePicturePath));

        assertThat(updated.getProfilePicturePath()).isEqualTo(profilePicturePath);
        assertThat(userRepository.insertCount).isEqualTo(1);
    }

    @Test
    void updateProfilePictureRejectsOtherUserPath() {
        UUID id = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        userService.ensureProfile(new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"));

        assertThatThrownBy(() -> userService.updateProfilePicture(
                new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"),
                new UpdateUserProfilePictureCommand(id, otherUserId + "/avatar.webp")))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Profile picture path");
    }

    @Test
    void updateProfilePictureRejectsNullObjectPath() {
        UUID id = UUID.randomUUID();
        userService.ensureProfile(new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"));

        assertThatThrownBy(() -> userService.updateProfilePicture(
                new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"),
                new UpdateUserProfilePictureCommand(id, null)))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Profile picture path");
    }

    @Test
    void updateProfilePictureRejectsNestedOrUnsupportedObjectPath() {
        UUID id = UUID.randomUUID();
        userService.ensureProfile(new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"));

        assertThatThrownBy(() -> userService.updateProfilePicture(
                new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"),
                new UpdateUserProfilePictureCommand(id, id + "/nested/avatar.gif")))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Profile picture path");
    }

    @Test
    void removeProfilePictureClearsObjectPath() {
        UUID id = UUID.randomUUID();
        userService.updateProfilePicture(
                new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"),
                new UpdateUserProfilePictureCommand(id, id + "/avatar.webp"));

        User updated = userService.removeProfilePicture(
                new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"));

        assertThat(updated.getProfilePicturePath()).isNull();
        assertThat(userRepository.insertCount).isEqualTo(1);
    }

    @Test
    void ensureProfileOnUnchangedEmailSkipsTheWrite() {
        UUID id = UUID.randomUUID();
        userService.ensureProfile(new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"));
        int writesAfterCreate = userRepository.identityWriteCount;

        // Same identity, same email: the common profile resolution case must not issue a write.
        User reread = userService.ensureProfile(
                new EnsureUserProfileCommand(id, "ada@example.com", "Ada", "Lovelace"));

        assertThat(reread.getEmail()).isEqualTo("ada@example.com");
        assertThat(userRepository.identityWriteCount).isEqualTo(writesAfterCreate);
    }

    @Test
    void getMissingUserThrows() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> userService.get(new GetUserQuery(id)))
                .isInstanceOf(UserException.class)
                .hasMessageContaining(id.toString());
    }

    static class FakeUserRepository {

        private final Map<UUID, User> usersById = new HashMap<>();
        private int insertCount;
        private int identityWriteCount;

        UserRepository proxy() {
            return (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(),
                    new Class<?>[]{UserRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findById" -> Optional.ofNullable(usersById.get(args[0]));
                        case "findByEmail" -> usersById.values().stream()
                                .filter(user -> user.getEmail().equals(args[0]))
                                .findFirst();
                        case "lockProfileProvisioning" -> 1;
                        // Mirrors the native INSERT ... ON CONFLICT: insert with names, or on conflict
                        // refresh only the email (names preserved) and advance updatedAt iff it changed.
                        case "insertOrRefreshFromIdentity" -> {
                            identityWriteCount++;
                            UUID id = (UUID) args[0];
                            String email = (String) args[1];
                            String firstName = (String) args[2];
                            String surname = (String) args[3];
                            Instant now = (Instant) args[4];
                            User existing = usersById.get(id);
                            if (existing == null) {
                                usersById.put(id, User.builder()
                                        .id(id)
                                        .email(email)
                                        .firstName(firstName)
                                        .surname(surname)
                                        .createdAt(now)
                                        .updatedAt(now)
                                        .build());
                                insertCount++;
                            } else {
                                existing.updateEmail(email, now);
                            }
                            yield null;
                        }
                        case "save", "saveAndFlush" -> {
                            User user = (User) args[0];
                            usersById.put(user.getId(), user);
                            insertCount++;
                            yield user;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
