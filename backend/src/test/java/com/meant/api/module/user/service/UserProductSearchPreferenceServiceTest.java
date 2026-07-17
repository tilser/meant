package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.entity.UserProductSearchPreference;
import com.meant.api.module.user.repository.UserProductSearchPreferenceRepository;
import com.meant.api.module.user.service.command.DeleteUserProductSearchPreferenceCommand;
import com.meant.api.module.user.service.command.SaveUserProductSearchPreferencesCommand;
import com.meant.api.module.user.service.command.UserProductSearchPreferenceCommand;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UserProductSearchPreferenceServiceTest {

    @Test
    void serializesAnUpsertBeforeCheckingWhetherTheScopedPreferenceExists() {
        PreferenceRepositoryHandler repository = new PreferenceRepositoryHandler();
        UserProductSearchPreferenceService service = new UserProductSearchPreferenceService(
                repository.proxy(), new ObjectMapper());
        UUID userId = UUID.randomUUID();

        service.upsert(new SaveUserProductSearchPreferencesCommand(
                userId,
                List.of(new UserProductSearchPreferenceCommand(
                        "footwear", UserProductSearchAttributeName.SIZE, List.of("10")))
        ));

        assertThat(repository.events).containsExactly("lock", "find", "save");
        assertThat(repository.saved).isNotNull();
    }

    @Test
    void serializesAScopedDeleteAndLeavesOtherScopesUntouched() {
        PreferenceRepositoryHandler repository = new PreferenceRepositoryHandler();
        UserProductSearchPreferenceService service = new UserProductSearchPreferenceService(
                repository.proxy(), new ObjectMapper());
        UUID userId = UUID.randomUUID();

        service.delete(new DeleteUserProductSearchPreferenceCommand(
                userId, "Footwear", UserProductSearchAttributeName.SIZE));

        assertThat(repository.events).containsExactly("lock", "delete");
        assertThat(repository.deletedUserId).isEqualTo(userId);
        assertThat(repository.deletedScope).isEqualTo("footwear");
        assertThat(repository.deletedAttribute).isEqualTo(UserProductSearchAttributeName.SIZE);
    }

    private static final class PreferenceRepositoryHandler implements InvocationHandler {

        private final List<String> events = new ArrayList<>();
        private UserProductSearchPreference saved;
        private UUID deletedUserId;
        private String deletedScope;
        private UserProductSearchAttributeName deletedAttribute;

        private UserProductSearchPreferenceRepository proxy() {
            return (UserProductSearchPreferenceRepository) Proxy.newProxyInstance(
                    UserProductSearchPreferenceRepository.class.getClassLoader(),
                    new Class<?>[]{UserProductSearchPreferenceRepository.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "lockUserPreferenceWrites" -> {
                    events.add("lock");
                    yield 1;
                }
                case "findByUserIdAndScopeAndAttributeName" -> {
                    events.add("find");
                    yield Optional.empty();
                }
                case "save" -> {
                    events.add("save");
                    saved = (UserProductSearchPreference) arguments[0];
                    yield saved;
                }
                case "deleteByUserIdAndScopeAndAttributeName" -> {
                    events.add("delete");
                    deletedUserId = (UUID) arguments[0];
                    deletedScope = (String) arguments[1];
                    deletedAttribute = (UserProductSearchAttributeName) arguments[2];
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }
}
