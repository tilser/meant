package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.constant.UserTasteSuggestionStatus;
import com.meant.api.module.user.entity.ShoppingFilter;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.entity.UserTasteSignal;
import com.meant.api.module.user.repository.ShoppingFilterRepository;
import com.meant.api.module.user.repository.UserTasteSignalRepository;
import com.meant.api.module.user.service.command.AcceptUserTasteSuggestionCommand;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTasteProfileServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000111");
    private static final Instant NOW = Instant.parse("2026-06-20T10:00:00Z");

    @Test
    void savesLearnFilterSignalsAndSuggestsOnlyFiltersThatAreNotExplicit() {
        FakeTasteSignalRepository signals = new FakeTasteSignalRepository();
        UserTasteProfileService service = new UserTasteProfileService(
                null,
                null,
                signals.proxy(),
                shoppingFilters()
        );

        service.recordSavedProduct(USER_ID, product(), NOW);
        service.recordSavedProduct(USER_ID, product(), NOW.plusSeconds(1));

        assertThat(signals.singleLookupCount).isZero();
        assertThat(signals.batchLookupCount).isEqualTo(2);
        assertThat(signals.saveAllCount).isEqualTo(2);
        assertThat(signals.savedInLastSaveAll).isOne();

        UserTasteProfileResult learnedProfile = service.profile(USER_ID, settings(List.of()));

        assertThat(learnedProfile.signals())
                .filteredOn(signal -> "linen".equals(signal.signalKey()))
                .singleElement()
                .satisfies(signal -> assertThat(signal.weight()).isEqualTo(2.8d));
        assertThat(learnedProfile.suggestions())
                .extracting("filterId")
                .containsExactly("linen");

        UserTasteProfileResult explicitProfile = service.profile(USER_ID, settings(List.of(new ShoppingFilterResult(
                "linen",
                "Linen",
                "Prefer linen.",
                "materials",
                "prefer",
                330
        ))));

        assertThat(explicitProfile.suggestions()).isEmpty();
    }

    @Test
    void recordSavedProductMergesDuplicateFilterSignalsInsideBatch() {
        FakeTasteSignalRepository signals = new FakeTasteSignalRepository();
        UserTasteProfileService service = new UserTasteProfileService(
                null,
                null,
                signals.proxy(),
                shoppingFilters()
        );

        service.recordSavedProduct(USER_ID, product(List.of("linen"), List.of(), List.of("linen")), NOW);

        UserTasteProfileResult profile = service.profile(USER_ID, settings(List.of()));

        assertThat(signals.singleLookupCount).isZero();
        assertThat(signals.batchLookupCount).isEqualTo(1);
        assertThat(signals.saveAllCount).isEqualTo(1);
        assertThat(signals.savedInLastSaveAll).isOne();
        assertThat(profile.signals())
                .filteredOn(signal -> "linen".equals(signal.signalKey()))
                .singleElement()
                .satisfies(signal -> {
                    assertThat(signal.weight()).isCloseTo(2.52d, within(0.0001d));
                    assertThat(signal.positiveCount()).isEqualTo(2);
                });
    }

    @Test
    void rejectsAllMatchingSuggestionsWithOneBulkUpdate() {
        FakeTasteSignalRepository signals = new FakeTasteSignalRepository();
        UserTasteProfileService service = new UserTasteProfileService(
                new FakeUserService(),
                null,
                signals.proxy(),
                shoppingFilters()
        );
        service.recordSavedProduct(USER_ID, product(), NOW);

        service.rejectSuggestion(
                new EnsureUserProfileCommand(USER_ID, "taste@example.com", "Taste", "User"),
                new AcceptUserTasteSuggestionCommand(USER_ID, "linen")
        );

        assertThat(signals.bulkSuggestionUpdateCount).isOne();
        assertThat(signals.individualSaveCount).isZero();
        assertThat(signals.signals)
                .singleElement()
                .extracting(UserTasteSignal::getSuggestionStatus)
                .isEqualTo(UserTasteSuggestionStatus.REJECTED);
    }

    private SaveUserProductCommand product() {
        return product(List.of("linen"), List.of(), List.of());
    }

    private SaveUserProductCommand product(
            List<String> satisfies,
            List<String> misses,
            List<String> provides
    ) {
        return new SaveUserProductCommand(
                USER_ID,
                "merchant.example:linen-shirt",
                "hash-linen-shirt",
                "Linen shirt",
                "Field Loom",
                "Shirts",
                "#f3f0e8",
                null,
                null,
                false,
                90,
                42.0d,
                1,
                satisfies,
                misses,
                "A strong match.",
                List.of(),
                List.of(),
                new SaveUserProductCommand.Review(4.8d, 200, "Well reviewed."),
                List.of(new SaveUserProductCommand.Offer(
                        "Field Loom",
                        42.0d,
                        "Tomorrow",
                        null,
                        "merchant.example",
                        null,
                        null,
                        true
                )),
                null,
                provides
        );
    }

    private UserSettingsResult settings(List<ShoppingFilterResult> filters) {
        return new UserSettingsResult(
                null,
                null,
                null,
                List.of(),
                filters,
                List.of(),
                List.of(),
                List.of(),
                NOW,
                NOW
        );
    }

    private ShoppingFilterRepository shoppingFilters() {
        ShoppingFilter linen = ShoppingFilter.builder()
                .id("linen")
                .label("Linen")
                .description("Prefer linen.")
                .category("materials")
                .polarity("prefer")
                .displayOrder(330)
                .createdAt(NOW)
                .build();
        return repository(ShoppingFilterRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findAll" -> List.of(linen);
            case "findAllById" -> {
                Iterable<?> ids = (Iterable<?>) args[0];
                List<String> requested = new ArrayList<>();
                ids.forEach(id -> requested.add((String) id));
                yield requested.contains(linen.getId()) ? List.of(linen) : List.of();
            }
            case "existsById" -> linen.getId().equals(args[0]);
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    static class FakeTasteSignalRepository {

        private final List<UserTasteSignal> signals = new ArrayList<>();
        private int singleLookupCount;
        private int batchLookupCount;
        private int saveAllCount;
        private int savedInLastSaveAll;
        private int individualSaveCount;
        private int bulkSuggestionUpdateCount;

        UserTasteSignalRepository proxy() {
            return repository(UserTasteSignalRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findByUserIdAndSignalTypeAndSignalKey" ->
                        findByUserIdAndSignalTypeAndSignalKey(
                                (UUID) args[0],
                                (UserTasteSignalType) args[1],
                                (String) args[2]);
                case "findByUserIdAndSignalTypeInAndSignalKeyIn" ->
                        findByUserIdAndSignalTypeInAndSignalKeyIn(
                                (UUID) args[0],
                                (Collection<UserTasteSignalType>) args[1],
                                (Collection<String>) args[2]);
                case "findByUserIdOrderByUpdatedAtDesc" -> signals.stream()
                        .filter(signal -> signal.getUserId().equals(args[0]))
                        .sorted(Comparator.comparing(UserTasteSignal::getUpdatedAt).reversed())
                        .toList();
                case "updateSuggestionStatus" -> updateSuggestionStatus(
                        (UUID) args[0],
                        (String) args[1],
                        (UserTasteSuggestionStatus) args[2],
                        (Instant) args[3]
                );
                case "save" -> {
                    individualSaveCount++;
                    yield save((UserTasteSignal) args[0]);
                }
                case "saveAll" -> saveAll((Iterable<UserTasteSignal>) args[0]);
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        private Optional<UserTasteSignal> findByUserIdAndSignalTypeAndSignalKey(
                UUID userId,
                UserTasteSignalType signalType,
                String signalKey
        ) {
            singleLookupCount++;
            return signals.stream()
                    .filter(signal -> signal.getUserId().equals(userId))
                    .filter(signal -> signal.getSignalType().equals(signalType))
                    .filter(signal -> signal.getSignalKey().equals(signalKey))
                    .findFirst();
        }

        private List<UserTasteSignal> findByUserIdAndSignalTypeInAndSignalKeyIn(
                UUID userId,
                Collection<UserTasteSignalType> signalTypes,
                Collection<String> signalKeys
        ) {
            batchLookupCount++;
            Set<UserTasteSignalType> requestedTypes = new LinkedHashSet<>(signalTypes);
            Set<String> requestedKeys = new LinkedHashSet<>(signalKeys);
            return signals.stream()
                    .filter(signal -> signal.getUserId().equals(userId))
                    .filter(signal -> requestedTypes.contains(signal.getSignalType()))
                    .filter(signal -> requestedKeys.contains(signal.getSignalKey()))
                    .toList();
        }

        private UserTasteSignal save(UserTasteSignal signal) {
            signals.removeIf(existing -> existing.getId().equals(signal.getId()));
            signals.add(signal);
            return signal;
        }

        private List<UserTasteSignal> saveAll(Iterable<UserTasteSignal> nextSignals) {
            List<UserTasteSignal> saved = new ArrayList<>();
            nextSignals.forEach(signal -> saved.add(save(signal)));
            saveAllCount++;
            savedInLastSaveAll = saved.size();
            return saved;
        }

        private int updateSuggestionStatus(
                UUID userId,
                String filterId,
                UserTasteSuggestionStatus status,
                Instant now
        ) {
            bulkSuggestionUpdateCount++;
            int changed = 0;
            for (UserTasteSignal signal : signals) {
                if (!signal.getUserId().equals(userId)
                        || !java.util.Objects.equals(signal.getSuggestedFilterId(), filterId)) {
                    continue;
                }
                if (status == UserTasteSuggestionStatus.ACCEPTED) {
                    signal.acceptSuggestion(now);
                } else if (status == UserTasteSuggestionStatus.REJECTED) {
                    signal.rejectSuggestion(now);
                }
                changed++;
            }
            return changed;
        }
    }

    private static final class FakeUserService extends UserService {

        private FakeUserService() {
            super(null);
        }

        @Override
        public User ensureProfile(EnsureUserProfileCommand command) {
            return null;
        }
    }

    private static <T> T repository(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                objectAwareHandler(handler)
        ));
    }

    private static InvocationHandler objectAwareHandler(InvocationHandler handler) {
        return (proxy, method, args) -> {
            if (method.getDeclaringClass().equals(Object.class)) {
                return objectMethod(proxy, method, args);
            }
            return handler.invoke(proxy, method, args);
        };
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "FakeRepository{" + proxy.getClass().getInterfaces()[0].getSimpleName() + "}";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }
}
