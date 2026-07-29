package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.entity.UserProductSearchQualification;
import com.meant.api.module.user.entity.UserProductSearchQualificationRequest;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserProductSearchQualificationRepository;
import com.meant.api.module.user.repository.UserProductSearchQualificationRequestRepository;
import com.meant.api.module.user.service.command.PersistUserProductSearchQualificationCommand;
import com.meant.api.module.user.service.command.CancelUserProductSearchQualificationCommand;
import com.meant.api.module.user.service.command.SaveUserProductSearchPreferencesCommand;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.query.FindPendingUserProductSearchQualificationQuery;
import com.meant.api.module.user.service.query.FindUserProductSearchQualificationByRequestQuery;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

class UserProductSearchQualificationPersistenceServiceTest {

    private final List<String> writes = new ArrayList<>();
    private final FakeQualificationRepository repository = new FakeQualificationRepository(writes);
    private final FakePreferenceService preferenceService = new FakePreferenceService(writes);
    private final UserProductSearchQualificationPlanCodec planCodec =
            new UserProductSearchQualificationPlanCodec(new ObjectMapper());
    private final FakeQualificationRequestRepository requestRepository =
            new FakeQualificationRequestRepository();
    private final UserProductSearchQualificationPersistenceService service =
            new UserProductSearchQualificationPersistenceService(
                    repository.proxy(), requestRepository.proxy(), planCodec, preferenceService);

    @Test
    void isolatesTheWriteTransactionSoAConcurrentInsertCanBeReconciledAfterRollback()
            throws NoSuchMethodException {
        Transactional transaction = UserProductSearchQualificationPersistenceService.class
                .getMethod("persist", PersistUserProductSearchQualificationCommand.class)
                .getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

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

    @Test
    void rejectsCancellationWhenTheObservedPendingQualificationAlreadyBecameReady() {
        UUID qualificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Instant observedPendingRevision = Instant.parse("2026-07-17T10:00:00Z");
        UserProductSearchQualification ready = UserProductSearchQualification.create(
                qualificationId,
                userId,
                conversationId,
                null,
                "football boots",
                UserProductSearchQualificationStatus.READY,
                planCodec.encode(plan(false)),
                "model",
                "v1",
                observedPendingRevision.plusSeconds(1)
        );
        repository.found = Optional.of(ready);

        assertThatThrownBy(() -> service.cancel(new CancelUserProductSearchQualificationCommand(
                qualificationId,
                userId,
                conversationId,
                null,
                observedPendingRevision
        )))
                .isInstanceOfSatisfying(UserException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(ready.getStatus()).isEqualTo(UserProductSearchQualificationStatus.READY);
        assertThat(repository.saved).isNull();
    }

    @Test
    void findsTheLatestPendingQualificationForTheExactConversationScope() {
        UUID qualificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UserProductSearchQualification pending = UserProductSearchQualification.create(
                qualificationId,
                userId,
                conversationId,
                null,
                "running shoes",
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                planCodec.encode(plan(false)),
                "model",
                "v1",
                Instant.parse("2026-07-29T12:00:00Z")
        );
        repository.pending = Optional.of(pending);

        var result = service.findLatestPending(
                new FindPendingUserProductSearchQualificationQuery(userId, conversationId, null));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().qualificationId()).isEqualTo(qualificationId);
        assertThat(repository.pendingLookupArguments).containsExactly(
                userId,
                conversationId,
                null,
                UserProductSearchQualificationStatus.NEEDS_INPUT
        );
    }

    @Test
    void atomicallyBindsTheTrustedAnswerRequestToTheQualificationItAdvanced() {
        UUID qualificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        repository.found = Optional.empty();
        requestRepository.forUpdate = Optional.empty();

        service.persist(new PersistUserProductSearchQualificationCommand(
                qualificationId,
                userId,
                conversationId,
                null,
                "running shoes",
                null,
                UserProductSearchQualificationStatus.READY,
                plan(false),
                "model",
                "v1",
                requestId,
                "46"
        ));

        assertThat(requestRepository.saved.getId()).isEqualTo(requestId);
        assertThat(requestRepository.saved.getQualificationId()).isEqualTo(qualificationId);
        assertThat(requestRepository.saved.getUserId()).isEqualTo(userId);
        assertThat(requestRepository.saved.getConversationId()).isEqualTo(conversationId);
        assertThat(requestRepository.saved.getMessage()).isEqualTo("46");
    }

    @Test
    void rejectsADistinctUnboundAnswerWhenAnotherRequestAlreadyMadeTheQualificationReady() {
        UUID qualificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID losingRequestId = UUID.randomUUID();
        Instant observedPendingRevision = Instant.parse("2026-07-29T12:00:00Z");
        UserProductSearchQualification ready = UserProductSearchQualification.create(
                qualificationId,
                userId,
                conversationId,
                null,
                "running shoes",
                UserProductSearchQualificationStatus.READY,
                planCodec.encode(plan(false)),
                "model",
                "v1",
                observedPendingRevision.plusSeconds(1)
        );
        repository.found = Optional.of(ready);
        requestRepository.forUpdate = Optional.empty();

        PersistUserProductSearchQualificationCommand losingAnswer =
                new PersistUserProductSearchQualificationCommand(
                        qualificationId,
                        userId,
                        conversationId,
                        null,
                        "running shoes",
                        observedPendingRevision,
                        UserProductSearchQualificationStatus.READY,
                        plan(false),
                        "model",
                        "v1",
                        losingRequestId,
                        "US size 10"
                );

        assertThatThrownBy(() -> service.persist(losingAnswer))
                .isInstanceOfSatisfying(UserException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(ready.getUpdatedAt()).isEqualTo(observedPendingRevision.plusSeconds(1));
        assertThat(requestRepository.saved).isNull();
        assertThat(repository.saved).isNull();
        assertThat(preferenceService.calls).isZero();
    }

    @Test
    void exactBoundRequestReplayMayReuseTheImmutableReadyQualification() {
        UUID qualificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant readyRevision = Instant.parse("2026-07-29T12:00:01Z");
        UserProductSearchQualification ready = UserProductSearchQualification.create(
                qualificationId,
                userId,
                conversationId,
                null,
                "running shoes",
                UserProductSearchQualificationStatus.READY,
                planCodec.encode(plan(false)),
                "winner-model",
                "winner-v1",
                readyRevision
        );
        UserProductSearchQualificationRequest binding = UserProductSearchQualificationRequest.create(
                requestId,
                qualificationId,
                userId,
                conversationId,
                null,
                "US size 10",
                readyRevision
        );
        repository.found = Optional.of(ready);
        requestRepository.forUpdate = Optional.of(binding);

        var replay = service.persist(new PersistUserProductSearchQualificationCommand(
                qualificationId,
                userId,
                conversationId,
                null,
                "running shoes",
                readyRevision.minusSeconds(1),
                UserProductSearchQualificationStatus.READY,
                plan(false),
                "loser-model",
                "loser-v1",
                requestId,
                "US size 10"
        ));

        assertThat(replay.status()).isEqualTo(UserProductSearchQualificationStatus.READY);
        assertThat(replay.updatedAt()).isEqualTo(readyRevision);
        assertThat(replay.model()).isEqualTo("winner-model");
        assertThat(ready.getUpdatedAt()).isEqualTo(readyRevision);
        assertThat(preferenceService.calls).isZero();
        assertThat(requestRepository.saved).isNull();
    }

    @Test
    void resolvesAReadyQualificationByItsAnswerRequestIdentity() {
        UUID qualificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UserProductSearchQualification ready = UserProductSearchQualification.create(
                qualificationId,
                userId,
                conversationId,
                null,
                "running shoes",
                UserProductSearchQualificationStatus.READY,
                planCodec.encode(plan(false)),
                "model",
                "v1",
                Instant.parse("2026-07-29T12:00:00Z")
        );
        repository.found = Optional.of(ready);
        requestRepository.found = Optional.of(
                UserProductSearchQualificationRequest.create(
                        requestId,
                        qualificationId,
                        userId,
                        conversationId,
                        null,
                        "46",
                        Instant.parse("2026-07-29T12:00:00Z")
                )
        );

        var replay = service.findByRequest(new FindUserProductSearchQualificationByRequestQuery(
                userId,
                conversationId,
                null,
                requestId,
                "46"
        ));

        assertThat(replay).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.qualificationId()).isEqualTo(qualificationId);
            assertThat(snapshot.status()).isEqualTo(UserProductSearchQualificationStatus.READY);
            assertThat(snapshot.originalQuery()).isEqualTo("running shoes");
        });
    }

    @Test
    void reconcilesAConcurrentFirstInsertOnlyThroughTheExactWinningRequestBinding() {
        UUID qualificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant readyRevision = Instant.parse("2026-07-29T12:00:01Z");
        UserProductSearchQualification ready = UserProductSearchQualification.create(
                qualificationId,
                userId,
                conversationId,
                null,
                "running shoes",
                UserProductSearchQualificationStatus.READY,
                planCodec.encode(plan(false)),
                "winner-model",
                "winner-v1",
                readyRevision
        );
        requestRepository.found = Optional.of(UserProductSearchQualificationRequest.create(
                requestId,
                qualificationId,
                userId,
                conversationId,
                null,
                "running shoes",
                readyRevision
        ));
        repository.found = Optional.of(ready);
        PersistUserProductSearchQualificationCommand losingWrite =
                new PersistUserProductSearchQualificationCommand(
                        qualificationId,
                        userId,
                        conversationId,
                        null,
                        "running shoes",
                        null,
                        UserProductSearchQualificationStatus.READY,
                        plan(false),
                        "loser-model",
                        "loser-v1",
                        requestId,
                        "running shoes"
                );

        var reconciled = service.reconcileConcurrentRequest(losingWrite);

        assertThat(reconciled).hasValueSatisfying(snapshot -> {
            assertThat(snapshot.qualificationId()).isEqualTo(qualificationId);
            assertThat(snapshot.status()).isEqualTo(UserProductSearchQualificationStatus.READY);
            assertThat(snapshot.model()).isEqualTo("winner-model");
        });
    }

    @Test
    void rejectsConcurrentInsertRecoveryWhenTheWinningBindingHasDifferentText() {
        UUID qualificationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        requestRepository.found = Optional.of(UserProductSearchQualificationRequest.create(
                requestId,
                qualificationId,
                userId,
                conversationId,
                null,
                "gaming laptop",
                Instant.parse("2026-07-29T12:00:01Z")
        ));

        PersistUserProductSearchQualificationCommand losingWrite =
                new PersistUserProductSearchQualificationCommand(
                        qualificationId,
                        userId,
                        conversationId,
                        null,
                        "running shoes",
                        null,
                        UserProductSearchQualificationStatus.READY,
                        plan(false),
                        "loser-model",
                        "loser-v1",
                        requestId,
                        "running shoes"
                );

        assertThatThrownBy(() -> service.reconcileConcurrentRequest(losingWrite))
                .isInstanceOfSatisfying(UserException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
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
                "v1",
                null,
                null
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
        private Optional<UserProductSearchQualification> pending = Optional.empty();
        private List<Object> pendingLookupArguments = List.of();
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
                case "findByIdAndUserId" -> found;
                case "findFirstByUserIdAndConversationIdAndMerchantIdAndStatusOrderByUpdatedAtDesc" -> {
                    pendingLookupArguments = new ArrayList<>(java.util.Arrays.asList(arguments));
                    yield pending;
                }
                case "save" -> {
                    saved = (UserProductSearchQualification) arguments[0];
                    writes.add("qualification");
                    yield saved;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }

    private static final class FakeQualificationRequestRepository implements InvocationHandler {

        private Optional<UserProductSearchQualificationRequest> found = Optional.empty();
        private Optional<UserProductSearchQualificationRequest> forUpdate = Optional.empty();
        private UserProductSearchQualificationRequest saved;

        private UserProductSearchQualificationRequestRepository proxy() {
            return (UserProductSearchQualificationRequestRepository) Proxy.newProxyInstance(
                    UserProductSearchQualificationRequestRepository.class.getClassLoader(),
                    new Class<?>[]{UserProductSearchQualificationRequestRepository.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "findById" -> found;
                case "findByIdForUpdate" -> forUpdate;
                case "save" -> {
                    saved = (UserProductSearchQualificationRequest) arguments[0];
                    yield saved;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }
}
