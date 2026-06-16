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
        assertThat(userRepository.saveCount).isEqualTo(1);
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
        assertThat(userRepository.saveCount).isEqualTo(1);
    }

    @Test
    void updateProfileMutatesNames() {
        UUID id = UUID.randomUUID();
        userService.upsert(new UpsertUserCommand(id, "ada@example.com", "Ada", "Lovelace"));

        User updated = userService.updateProfile(new UpdateUserProfileCommand(id, "Augusta", "Byron"));

        assertThat(updated.getFirstName()).isEqualTo("Augusta");
        assertThat(updated.getSurname()).isEqualTo("Byron");
    }

    @Test
    void getMissingUserThrows() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> userService.get(new GetUserQuery(id)))
                .isInstanceOf(UserException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void updateProfileMissingUserThrows() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> userService.updateProfile(new UpdateUserProfileCommand(id, "A", "B")))
                .isInstanceOf(UserException.class);
    }

    static class FakeUserRepository {

        private final Map<UUID, User> usersById = new HashMap<>();
        private int saveCount;

        UserRepository proxy() {
            return (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(),
                    new Class<?>[]{UserRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findById" -> Optional.ofNullable(usersById.get(args[0]));
                        case "findByEmail" -> usersById.values().stream()
                                .filter(user -> user.getEmail().equals(args[0]))
                                .findFirst();
                        case "save" -> {
                            User user = (User) args[0];
                            usersById.put(user.getId(), user);
                            saveCount++;
                            yield user;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
