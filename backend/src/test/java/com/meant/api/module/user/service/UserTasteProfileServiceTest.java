package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.entity.ShoppingFilter;
import com.meant.api.module.user.entity.UserTasteSignal;
import com.meant.api.module.user.repository.ShoppingFilterRepository;
import com.meant.api.module.user.repository.UserTasteSignalRepository;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    private SaveUserProductCommand product() {
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
                List.of("linen"),
                List.of(),
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
                List.of()
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

        UserTasteSignalRepository proxy() {
            return repository(UserTasteSignalRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findByUserIdAndSignalTypeAndSignalKey" -> signals.stream()
                        .filter(signal -> signal.getUserId().equals(args[0]))
                        .filter(signal -> signal.getSignalType().equals(args[1]))
                        .filter(signal -> signal.getSignalKey().equals(args[2]))
                        .findFirst();
                case "findByUserIdOrderByUpdatedAtDesc" -> signals.stream()
                        .filter(signal -> signal.getUserId().equals(args[0]))
                        .sorted(Comparator.comparing(UserTasteSignal::getUpdatedAt).reversed())
                        .toList();
                case "save" -> save((UserTasteSignal) args[0]);
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        private UserTasteSignal save(UserTasteSignal signal) {
            signals.removeIf(existing -> existing.getId().equals(signal.getId()));
            signals.add(signal);
            return signal;
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
