package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.entity.ShoppingFilter;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.entity.UserSettings;
import com.meant.api.module.user.entity.UserSettingsLocation;
import com.meant.api.module.user.repository.ShoppingFilterRepository;
import com.meant.api.module.user.repository.UserSettingsLocationRepository;
import com.meant.api.module.user.repository.UserSettingsRepository;
import com.meant.api.module.user.repository.UserShoppingFilterRepository;
import com.meant.api.module.user.service.command.AddUserSettingsFilterCommand;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.UpdateUserSettingsCommand;
import com.meant.api.module.user.service.command.UserLocationCommand;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserSettingsServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000023");
    private static final Instant NOW = Instant.parse("2026-07-05T08:00:00Z");

    private FakeUserShoppingFilterRepository userShoppingFilterRepository;
    private FakeShoppingFilterRepository shoppingFilterRepository;
    private FakeUserSettingsLocationRepository userSettingsLocationRepository;
    private UserSettingsService service;

    @BeforeEach
    void setUp() {
        UserSettings settings = UserSettings.builder()
                .userId(USER_ID)
                .budget(120)
                .currency("USD")
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
        userShoppingFilterRepository = new FakeUserShoppingFilterRepository(List.of("organic"));
        userSettingsLocationRepository = new FakeUserSettingsLocationRepository();
        shoppingFilterRepository = new FakeShoppingFilterRepository(List.of(
                shoppingFilter("organic", 10),
                shoppingFilter("gluten-free", 20),
                shoppingFilter("cotton", 30)
        ));
        service = new UserSettingsService(
                new FakeUserService(),
                userSettingsRepository(settings),
                userSettingsLocationRepository.proxy(),
                userShoppingFilterRepository.proxy(),
                shoppingFilterRepository.proxy()
        );
    }

    @Test
    void updateReusesActiveFilterAndCatalogSnapshotsForValidationAndResponse() {
        UserSettingsResult result = service.update(
                profileCommand(),
                new UpdateUserSettingsCommand(
                        USER_ID,
                        null,
                        false,
                        null,
                        null,
                        null,
                        new LinkedHashSet<>(List.of("organic", "cotton")),
                        new LinkedHashSet<>(List.of("gluten-free")),
                        List.of()
                )
        );

        assertThat(userShoppingFilterRepository.findFilterIdsByUserIdCalls).isEqualTo(1);
        assertThat(shoppingFilterRepository.findAllByDisplayOrderCalls).isEqualTo(1);
        assertThat(shoppingFilterRepository.findAllByIdCalls).isZero();
        assertThat(userShoppingFilterRepository.flushCalls).isZero();
        assertThat(userShoppingFilterRepository.insertIfMissingCalls).isOne();
        assertThat(userShoppingFilterRepository.savedFilterIds)
                .containsExactly("organic", "cotton", "gluten-free");
        assertThat(result.filters())
                .extracting("id")
                .containsExactly("organic", "gluten-free", "cotton");
        assertThat(result.availableFilters())
                .extracting("id")
                .containsExactly("organic", "gluten-free", "cotton");
    }

    @Test
    void addActiveFilterUsesOneSettingsAndFilterReadPass() {
        UserSettingsResult result = service.addActiveFilter(
                profileCommand(),
                new AddUserSettingsFilterCommand(USER_ID, "cotton")
        );

        assertThat(userShoppingFilterRepository.findFilterIdsByUserIdCalls).isOne();
        assertThat(shoppingFilterRepository.findAllByDisplayOrderCalls).isOne();
        assertThat(userSettingsLocationRepository.findCalls).isOne();
        assertThat(userShoppingFilterRepository.insertIfMissingCalls).isOne();
        assertThat(result.filters())
                .extracting("id")
                .containsExactly("organic", "cotton");
    }

    @Test
    void updateNormalizesAndPersistsPreferredCurrency() {
        UserSettingsResult result = service.update(
                profileCommand(),
                new UpdateUserSettingsCommand(
                        USER_ID,
                        null,
                        false,
                        null,
                        "eur",
                        null,
                        null,
                        null,
                        Set.of(),
                        List.of()
                )
        );

        assertThat(result.currency()).isEqualTo("EUR");
    }

    @Test
    void updateReusesTheLocationSnapshotLoadedForChangeDetection() {
        UserSettingsResult result = service.update(
                profileCommand(),
                new UpdateUserSettingsCommand(
                        USER_ID,
                        null,
                        false,
                        null,
                        null,
                        List.of(new UserLocationCommand(
                                "geonames:3067696",
                                "Czechia",
                                "CZ",
                                "10",
                                null,
                                "Prague",
                                "Prague"
                        )),
                        null,
                        Set.of(),
                        List.of()
                )
        );

        assertThat(userSettingsLocationRepository.findCalls).isOne();
        assertThat(userSettingsLocationRepository.deleteCalls).isOne();
        assertThat(userSettingsLocationRepository.saveAllCalls).isOne();
        assertThat(result.locations())
                .containsExactly(new UserLocationResult(
                        "geonames:3067696",
                        "Czechia",
                        "CZ",
                        "10",
                        null,
                        "Prague",
                        "Prague"
                ));
    }

    private EnsureUserProfileCommand profileCommand() {
        return new EnsureUserProfileCommand(USER_ID, "settings@example.com", "Settings", "User");
    }

    private static ShoppingFilter shoppingFilter(String id, int displayOrder) {
        return ShoppingFilter.builder()
                .id(id)
                .label(id)
                .description(id + " description")
                .category("shopping")
                .polarity("prefer")
                .displayOrder(displayOrder)
                .createdAt(NOW)
                .build();
    }

    private static UserSettingsRepository userSettingsRepository(UserSettings settings) {
        return repository(UserSettingsRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findById" -> Optional.of(settings);
            default -> throw unsupported(method);
        });
    }

    private static <T> T repository(Class<T> repositoryType, InvocationHandler handler) {
        return repositoryType.cast(Proxy.newProxyInstance(
                repositoryType.getClassLoader(),
                new Class<?>[]{repositoryType},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass().equals(Object.class)) {
                        return objectMethod(repositoryType, proxy, method, args);
                    }
                    return handler.invoke(proxy, method, args);
                }
        ));
    }

    private static Object objectMethod(Class<?> repositoryType, Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> repositoryType.getSimpleName() + "Proxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw unsupported(method);
        };
    }

    private static UnsupportedOperationException unsupported(Method method) {
        return new UnsupportedOperationException(method.getName());
    }

    static class FakeUserService extends UserService {

        FakeUserService() {
            super(null);
        }

        @Override
        public User ensureProfile(EnsureUserProfileCommand command) {
            return null;
        }
    }

    static class FakeUserShoppingFilterRepository {

        private List<String> activeFilterIds;
        private List<String> savedFilterIds = List.of();
        private int findFilterIdsByUserIdCalls;
        private int flushCalls;
        private int insertIfMissingCalls;

        FakeUserShoppingFilterRepository(List<String> activeFilterIds) {
            this.activeFilterIds = new ArrayList<>(activeFilterIds);
        }

        @SuppressWarnings("unchecked")
        UserShoppingFilterRepository proxy() {
            return repository(UserShoppingFilterRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findFilterIdsByUserId" -> findFilterIdsByUserId();
                case "deleteByIdUserId" -> {
                    activeFilterIds = new ArrayList<>();
                    yield null;
                }
                case "flush" -> {
                    flushCalls++;
                    yield null;
                }
                case "insertIfMissing" -> insertIfMissing((Collection<String>) args[1]);
                default -> throw unsupported(method);
            });
        }

        private List<String> findFilterIdsByUserId() {
            findFilterIdsByUserIdCalls++;
            return List.copyOf(activeFilterIds);
        }

        private int insertIfMissing(Collection<String> filterIds) {
            insertIfMissingCalls++;
            savedFilterIds = List.copyOf(filterIds);
            activeFilterIds = new ArrayList<>(savedFilterIds);
            return savedFilterIds.size();
        }
    }

    static class FakeUserSettingsLocationRepository {

        private final List<UserSettingsLocation> locations = new ArrayList<>();
        private int findCalls;
        private int deleteCalls;
        private int saveAllCalls;

        @SuppressWarnings("unchecked")
        UserSettingsLocationRepository proxy() {
            return repository(UserSettingsLocationRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findByIdUserIdOrderByDisplayOrderAsc" -> {
                    findCalls++;
                    yield List.copyOf(locations);
                }
                case "deleteByIdUserId" -> {
                    deleteCalls++;
                    locations.clear();
                    yield null;
                }
                case "saveAll" -> {
                    saveAllCalls++;
                    ((Iterable<UserSettingsLocation>) args[0]).forEach(locations::add);
                    yield List.copyOf(locations);
                }
                default -> throw unsupported(method);
            });
        }
    }

    static class FakeShoppingFilterRepository {

        private final List<ShoppingFilter> filters;
        private int findAllByDisplayOrderCalls;
        private int findAllByIdCalls;

        FakeShoppingFilterRepository(List<ShoppingFilter> filters) {
            this.filters = filters;
        }

        ShoppingFilterRepository proxy() {
            return repository(ShoppingFilterRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findAllByOrderByDisplayOrderAsc" -> {
                    findAllByDisplayOrderCalls++;
                    yield filters;
                }
                case "findAllById" -> {
                    findAllByIdCalls++;
                    yield List.of();
                }
                default -> throw unsupported(method);
            });
        }
    }
}
