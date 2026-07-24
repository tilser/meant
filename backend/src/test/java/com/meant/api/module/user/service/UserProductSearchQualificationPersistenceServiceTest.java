package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.entity.UserProductSearchQualification;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserProductSearchQualificationRepository;
import com.meant.api.module.user.service.command.PersistUserProductSearchQualificationCommand;
import com.meant.api.module.user.service.command.CancelUserProductSearchQualificationCommand;
import com.meant.api.module.user.service.command.SaveUserProductSearchPreferencesCommand;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

class UserProductSearchQualificationPersistenceServiceTest {

    private final List<String> writes = new ArrayList<>();
    private final FakeQualificationRepository repository = new FakeQualificationRepository(writes);
    private final FakePreferenceService preferenceService = new FakePreferenceService(writes);
    private final UserProductSearchQualificationPlanCodec planCodec =
            new UserProductSearchQualificationPlanCodec(new ObjectMapper());
    private final UserProductSearchQualificationPersistenceService service =
            new UserProductSearchQualificationPersistenceService(
                    repository.proxy(), planCodec, preferenceService);

    @Test
    void appliesDurablePreferencesInsideTheQualificationWriteBoundary() {
        UUID qualificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        repository.found = Optional.empty();

        service.persist(command(
                qualificationId,
                userId,
                conversationId,
                null,
                UserProductSearchQualificationStatus.READY,
                plan(true)
        ));

        assertThat(writes).containsExactly("preferences", "qualification");
        assertThat(preferenceService.saved.userId()).isEqualTo(userId);
        assertThat(preferenceService.saved.preferences()).singleElement().satisfies(preference -> {
            assertThat(preference.scope()).isEqualTo("footwear");
            assertThat(preference.attributeName()).isEqualTo(UserProductSearchAttributeName.SIZE);
            assertThat(preference.values()).containsExactly("10");
        });
        assertThat(repository.saved).isNotNull();
    }

    @Test
    void rejectsAStalePendingContinuationBeforeChangingState() {
        UUID qualificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Instant currentRevision = Instant.parse("2026-07-17T10:00:00Z");
        UserProductSearchQualification existing = UserProductSearchQualification.create(
                qualificationId,
                userId,
                conversationId,
                null,
                "running shoes",
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                planCodec.encode(plan(false)),
                "model",
                "v1",
                currentRevision
        );
        repository.found = Optional.of(existing);

        PersistUserProductSearchQualificationCommand stale = command(
                qualificationId,
                userId,
                conversationId,
                currentRevision.minusSeconds(1),
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                plan(false)
        );

        assertThatThrownBy(() -> service.persist(stale))
                .isInstanceOfSatisfying(UserException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(existing.getUpdatedAt()).isEqualTo(currentRevision);
        assertThat(preferenceService.calls).isZero();
        assertThat(repository.saved).isNull();
    }

    @Test
    void rejectsAConcurrentCancellationAtAStaleRevision() {
        UUID qualificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Instant currentRevision = Instant.parse("2026-07-17T10:00:00Z");
        UserProductSearchQualification existing = UserProductSearchQualification.create(
                qualificationId,
                userId,
                conversationId,
                null,
                "football boots",
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                planCodec.encode(plan(false)),
                "model",
                "v1",
                currentRevision
        );
        repository.found = Optional.of(existing);

        assertThatThrownBy(() -> service.cancel(new CancelUserProductSearchQualificationCommand(
                qualificationId,
                userId,
                conversationId,
                null,
                currentRevision.minusSeconds(1)
        )))
                .isInstanceOfSatisfying(UserException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(existing.getStatus()).isEqualTo(UserProductSearchQualificationStatus.NEEDS_INPUT);
        assertThat(repository.saved).isNull();
    }

    private PersistUserProductSearchQualificationCommand command(
            UUID qualificationId,
            UUID userId,
            UUID conversationId,
            Instant expectedUpdatedAt,
            UserProductSearchQualificationStatus status,
            UserProductSearchQualificationPlan plan
    ) {
        return new PersistUserProductSearchQualificationCommand(
                qualificationId,
                userId,
                conversationId,
                null,
                "running shoes",
                expectedUpdatedAt,
                status,
                plan,
                "model",
                "v1"
        );
    }

    private UserProductSearchQualificationPlan plan(boolean durableSize) {
        UserProductSearchFilterState attributesState = durableSize
                ? UserProductSearchFilterState.VALUE
                : UserProductSearchFilterState.NOT_APPLICABLE;
        return new UserProductSearchQualificationPlan(
                "running shoes",
                "Ready to search.",
                List.of(),
                new UserProductSearchQualificationPlan.AvailableFilter(UserProductSearchFilterState.VALUE, true),
                new UserProductSearchQualificationPlan.ConditionFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null),
                new UserProductSearchQualificationPlan.LocationsFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null, null),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.AttributesFilter(
                        attributesState,
                        durableSize
                                ? List.of(new UserProductSearchQualificationPlan.Attribute(
                                        UserProductSearchAttributeName.SIZE, List.of("10")))
                                : List.of()),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null, null),
                new UserProductSearchQualificationPlan.PriceTierFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                durableSize
                        ? List.of(new UserProductSearchQualificationPlan.DurableAttribute(
                                "footwear", UserProductSearchAttributeName.SIZE, List.of("10")))
                        : List.of()
        );
    }

    private static final class FakePreferenceService extends UserProductSearchPreferenceService {

        private final List<String> writes;
        private int calls;
        private SaveUserProductSearchPreferencesCommand saved;

        private FakePreferenceService(List<String> writes) {
            super(null, null);
            this.writes = writes;
        }

        @Override
        public void upsert(SaveUserProductSearchPreferencesCommand command) {
            calls++;
            saved = command;
            writes.add("preferences");
        }
    }

    private static final class FakeQualificationRepository implements InvocationHandler {

        private final List<String> writes;
        private Optional<UserProductSearchQualification> found = Optional.empty();
        private UserProductSearchQualification saved;

        private FakeQualificationRepository(List<String> writes) {
            this.writes = writes;
        }

        private UserProductSearchQualificationRepository proxy() {
            return (UserProductSearchQualificationRepository) Proxy.newProxyInstance(
                    UserProductSearchQualificationRepository.class.getClassLoader(),
                    new Class<?>[]{UserProductSearchQualificationRepository.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "findByIdForUpdate" -> found;
                case "save" -> {
                    saved = (UserProductSearchQualification) arguments[0];
                    writes.add("qualification");
                    yield saved;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }
}
