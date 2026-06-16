package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserRepository;
import com.meant.api.module.user.service.command.UpdateUserProfileCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
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
    void upsertCreatesUserOnFirstSight() {
        UUID id = UUID.randomUUID();

        User created = userService.upsert(new UpsertUserCommand(id, "ada@example.com", "Ada", "Lovelace"));

        assertThat(created.getId()).isEqualTo(id);
        assertThat(created.getEmail()).isEqualTo("ada@example.com");
        assertThat(created.getFirstName()).isEqualTo("Ada");
        assertThat(created.getSurname()).isEqualTo("Lovelace");
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
        assertThat(userRepository.insertCount).isEqualTo(1);
    }

    @Test
    void upsertRefreshesEmailButPreservesProfileOnSecondCall() {
        UUID id = UUID.randomUUID();
        userService.upsert(new UpsertUserCommand(id, "ada@example.com", "Ada", "Lovelace"));

        User updated = userService.upsert(new UpsertUserCommand(id, "ada@new.com", "ShouldBeIgnored", "Ignored"));

        assertThat(updated.getEmail()).isEqualTo("ada@new.com");
        assertThat(updated.getFirstName()).isEqualTo("Ada");
        assertThat(updated.getSurname()).isEqualTo("Lovelace");
        // First call inserts; second call mutates the existing row without inserting again.
        assertThat(userRepository.insertCount).isEqualTo(1);
    }

    @Test
    void updateProfileMutatesNamesOnExistingUser() {
        UUID id = UUID.randomUUID();
        userService.upsert(new UpsertUserCommand(id, "ada@example.com", "Ada", "Lovelace"));

        User updated = userService.updateProfile(
                new UpsertUserCommand(id, "ada@example.com", "Ada", "Lovelace"),
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
                new UpsertUserCommand(id, "grace@example.com", "Grace", "Hopper"),
                new UpdateUserProfileCommand(id, "Grace", "Murray Hopper"));

        assertThat(created.getId()).isEqualTo(id);
        assertThat(created.getEmail()).isEqualTo("grace@example.com");
        assertThat(created.getSurname()).isEqualTo("Murray Hopper");
        assertThat(userRepository.insertCount).isEqualTo(1);
    }

    @Test
    void upsertOnUnchangedEmailSkipsTheWrite() {
        UUID id = UUID.randomUUID();
        userService.upsert(new UpsertUserCommand(id, "ada@example.com", "Ada", "Lovelace"));
        int writesAfterCreate = userRepository.upsertCallCount;

        // Same identity, same email: the common upsert-on-read case must not issue a write.
        User reread = userService.upsert(new UpsertUserCommand(id, "ada@example.com", "Ada", "Lovelace"));

        assertThat(reread.getEmail()).isEqualTo("ada@example.com");
        assertThat(userRepository.upsertCallCount).isEqualTo(writesAfterCreate);
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
        private int upsertCallCount;

        UserRepository proxy() {
            return (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(),
                    new Class<?>[]{UserRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findById" -> Optional.ofNullable(usersById.get(args[0]));
                        case "findByEmail" -> usersById.values().stream()
                                .filter(user -> user.getEmail().equals(args[0]))
                                .findFirst();
                        // Mirrors the native INSERT ... ON CONFLICT: insert with names, or on conflict
                        // refresh only the email (names preserved) and advance updatedAt iff it changed.
                        case "upsertFromIdentity" -> {
                            upsertCallCount++;
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
