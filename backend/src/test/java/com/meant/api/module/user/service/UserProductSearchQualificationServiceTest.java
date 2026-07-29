package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchFilterKind;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.exception.UnsupportedProductSearchCurrencyException;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.PersistUserProductSearchQualificationCommand;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import com.meant.api.module.user.service.command.SaveUserProductSearchPreferencesCommand;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationModelResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationSnapshot;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserProductSearchQualificationServiceTest {

    @Test
    void firstTurnAlwaysCallsModelAndPersistsNeedsInputWithoutSearching() {
        UserSettingsResult settings = settings();
        FakeUserSettingsService settingsService = new FakeUserSettingsService(settings);
        UserProductSearchPreferenceResult footwearSize = new UserProductSearchPreferenceResult(
                "footwear", UserProductSearchAttributeName.SIZE, List.of("10"));
        FakePreferenceService preferenceService = new FakePreferenceService(List.of(footwearSize));
        UserProductSearchQualificationPlan plan = plan(
                UserProductSearchFilterState.MISSING,
                List.of(new UserProductSearchQualificationPlan.DurableAttribute(
                        "footwear", UserProductSearchAttributeName.SIZE, List.of("10")))
        );
        FakeModelService modelService = new FakeModelService(new UserProductSearchQualificationModelResult(
                plan, "qualification-model", "qualification-v1"));
        FakePersistenceService persistenceService = new FakePersistenceService();
        UserProductSearchQualificationService service = new UserProductSearchQualificationService(
                settingsService,
                preferenceService,
                modelService,
                persistenceService,
                catalogInputBuilder()
        );
        EnsureUserProfileCommand profile = profile();

        var result = service.qualify(profile, new QualifyUserProductSearchCommand(
                profile.id(), UUID.randomUUID(), null, "running shoes", null));

        assertThat(result.status()).isEqualTo(UserProductSearchQualificationStatus.NEEDS_INPUT);
        assertThat(result.missingFilters()).containsExactly(UserProductSearchFilterKind.PRICE);
        assertThat(result.effectiveQuery()).isEqualTo("running shoes");
        assertThat(settingsService.calls).isEqualTo(1);
        assertThat(modelService.calls).isEqualTo(1);
        assertThat(modelService.lastQuery.originalQuery()).isEqualTo("running shoes");
        assertThat(modelService.lastQuery.previousPlan()).isNull();
        assertThat(modelService.lastQuery.durablePreferences()).containsExactly(footwearSize);
        assertThat(persistenceService.persistCalls).isEqualTo(1);
        assertThat(persistenceService.lastPersistedCommand.expectedUpdatedAt()).isNull();
        assertThat(persistenceService.lastPersistedCommand.plan().durableAttributes())
                .singleElement().satisfies(preference -> {
            assertThat(preference.scope()).isEqualTo("footwear");
            assertThat(preference.name()).isEqualTo(UserProductSearchAttributeName.SIZE);
            assertThat(preference.values()).containsExactly("10");
        });
    }

    @Test
    void firstTurnPersistsTheRequestedMerchantScope() {
        FakePersistenceService persistenceService = new FakePersistenceService();
        UserProductSearchQualificationService service = new UserProductSearchQualificationService(
                new FakeUserSettingsService(settings()),
                new FakePreferenceService(List.of()),
                new FakeModelService(new UserProductSearchQualificationModelResult(
                        plan(UserProductSearchFilterState.ANY),
                        "qualification-model",
                        "qualification-v1"
                )),
                persistenceService,
                catalogInputBuilder()
        );
        EnsureUserProfileCommand profile = profile();
        UUID merchantId = UUID.fromString("00000000-0000-4000-8000-000000000002");

        var result = service.qualify(profile, new QualifyUserProductSearchCommand(
                profile.id(), UUID.randomUUID(), null, "running shoes", merchantId));

        assertThat(result.status()).isEqualTo(UserProductSearchQualificationStatus.READY);
        assertThat(persistenceService.lastPersistedCommand.merchantId()).isEqualTo(merchantId);
    }

    @Test
    void readyQualificationIsImmutableAndDoesNotCallModelAgain() {
        FakeUserSettingsService settingsService = new FakeUserSettingsService(settings());
        FakePreferenceService preferenceService = new FakePreferenceService(List.of());
        FakeModelService modelService = new FakeModelService(null);
        FakePersistenceService persistenceService = new FakePersistenceService();
        UserProductSearchQualificationService service = new UserProductSearchQualificationService(
                settingsService,
                preferenceService,
                modelService,
                persistenceService,
                catalogInputBuilder()
        );
        EnsureUserProfileCommand profile = profile();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        UserProductSearchQualificationSnapshot ready = new UserProductSearchQualificationSnapshot(
                qualificationId,
                profile.id(),
                conversationId,
                null,
                "running shoes",
                UserProductSearchQualificationStatus.READY,
                plan(UserProductSearchFilterState.ANY),
                "qualification-model",
                "qualification-v1",
                Instant.parse("2026-07-17T10:00:00Z"),
                Instant.parse("2026-07-17T10:00:00Z")
        );
        persistenceService.found = Optional.of(ready);

        var retried = service.qualify(profile, new QualifyUserProductSearchCommand(
                profile.id(), conversationId, qualificationId, "retry", null));

        assertThat(retried.status()).isEqualTo(UserProductSearchQualificationStatus.READY);
        assertThat(retried.qualificationId()).isEqualTo(qualificationId);
        assertThat(settingsService.calls).isZero();
        assertThat(preferenceService.listCalls).isZero();
        assertThat(preferenceService.upsertCalls).isZero();
        assertThat(modelService.calls).isZero();
        assertThat(persistenceService.persistCalls).isZero();
        assertThat(persistenceService.refreshReadyCalls).isEqualTo(1);
    }

    @Test
    void rejectsAnOutdatedReadyPlanBeforeRefreshingOrCallingTheModel() {
        FakeUserSettingsService settingsService = new FakeUserSettingsService(settings());
        FakePreferenceService preferenceService = new FakePreferenceService(List.of());
        FakeModelService modelService = new FakeModelService(null);
        FakePersistenceService persistenceService = new FakePersistenceService();
        UserProductSearchQualificationService service = new UserProductSearchQualificationService(
                settingsService,
                preferenceService,
                modelService,
                persistenceService,
                catalogInputBuilder()
        );
        EnsureUserProfileCommand profile = profile();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        UserProductSearchQualificationSnapshot ready = new UserProductSearchQualificationSnapshot(
                qualificationId,
                profile.id(),
                conversationId,
                null,
                "running shoes",
                UserProductSearchQualificationStatus.READY,
                withSchemaVersion(plan(UserProductSearchFilterState.ANY), 1),
                "qualification-model",
                "qualification-v1",
                Instant.parse("2026-07-17T10:00:00Z"),
                Instant.parse("2026-07-17T10:00:00Z")
        );
        persistenceService.found = Optional.of(ready);

        assertThatThrownBy(() -> service.qualify(profile, new QualifyUserProductSearchCommand(
                profile.id(), conversationId, qualificationId, "retry", null)))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("outdated plan");
        assertThat(modelService.calls).isZero();
        assertThat(persistenceService.refreshReadyCalls).isZero();
        assertThat(persistenceService.persistCalls).isZero();
    }

    @Test
    void readyAccessorDelegatesToAuthoritativePersistenceBoundary() {
        FakePersistenceService persistenceService = new FakePersistenceService();
        UserProductSearchQualificationService service = new UserProductSearchQualificationService(
                new FakeUserSettingsService(settings()),
                new FakePreferenceService(List.of()),
                new FakeModelService(null),
                persistenceService,
                catalogInputBuilder()
        );
        GetUserProductSearchQualificationQuery query = new GetUserProductSearchQualificationQuery(
                UUID.randomUUID(), UUID.randomUUID());
        UserProductSearchQualificationSnapshot expected = new UserProductSearchQualificationSnapshot(
                query.qualificationId(),
                query.userId(),
                UUID.randomUUID(),
                null,
                "running shoes",
                UserProductSearchQualificationStatus.READY,
                plan(UserProductSearchFilterState.ANY),
                "qualification-model",
                "qualification-v1",
                Instant.parse("2026-07-17T10:00:00Z"),
                Instant.parse("2026-07-17T10:00:00Z")
        );
        persistenceService.ready = expected;

        assertThat(service.getReady(query)).isSameAs(expected);
        assertThat(persistenceService.getReadyCalls).isEqualTo(1);
    }

    @Test
    void rejectsNonUsdBeforeCallingTheQualificationModel() {
        FakeModelService modelService = new FakeModelService(null);
        UserProductSearchQualificationService service = new UserProductSearchQualificationService(
                new FakeUserSettingsService(settings()),
                new FakePreferenceService(List.of()),
                modelService,
                new FakePersistenceService(),
                catalogInputBuilder()
        );
        EnsureUserProfileCommand profile = profile();

        assertThatThrownBy(() -> service.qualify(profile, new QualifyUserProductSearchCommand(
                profile.id(), UUID.randomUUID(), null, "running shoes under 100 EUR", null)))
                .isInstanceOf(UnsupportedProductSearchCurrencyException.class);
        assertThat(modelService.calls).isZero();
    }

    private EnsureUserProfileCommand profile() {
        return new EnsureUserProfileCommand(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                "user@example.com",
                "Test",
                "User"
        );
    }

    private UserSettingsResult settings() {
        Instant now = Instant.parse("2026-07-17T10:00:00Z");
        return new UserSettingsResult(
                999,
                "men",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                now,
                now
        );
    }

    private UserProductSearchCatalogInputBuilder catalogInputBuilder() {
        return new UserProductSearchCatalogInputBuilder(new UserProductSearchHashService(
                new UserProductSearchProperties(
                        "search-v1",
                        "qualification-v1",
                        "explanation-v1",
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(1),
                        100,
                        Duration.ofSeconds(30),
                        10,
                        5,
                        5,
                        Duration.ofDays(1),
                        Duration.ofDays(7),
                        10,
                        2,
                        80
                )
        ));
    }

    private UserProductSearchQualificationPlan plan(UserProductSearchFilterState priceState) {
        return plan(priceState, List.of());
    }

    private UserProductSearchQualificationPlan plan(
            UserProductSearchFilterState priceState,
            List<UserProductSearchQualificationPlan.DurableAttribute> durableAttributes
    ) {
        return new UserProductSearchQualificationPlan(
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION,
                "running shoes",
                priceState == UserProductSearchFilterState.MISSING
                        ? "What is your USD budget?"
                        : "Ready to search.",
                priceState == UserProductSearchFilterState.MISSING ? List.of("Under $100", "Any budget") : List.of(),
                priceState == UserProductSearchFilterState.MISSING
                        ? List.of(com.meant.api.module.user.constant.UserProductSearchQuestionTarget.PRICE)
                        : List.of(),
                new UserProductSearchQualificationPlan.AvailableFilter(
                        UserProductSearchFilterState.VALUE, true),
                new UserProductSearchQualificationPlan.ConditionFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null),
                new UserProductSearchQualificationPlan.LocationsFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.PriceFilter(priceState, null, null),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.AttributesFilter(
                        durableAttributes.isEmpty()
                                ? UserProductSearchFilterState.NOT_APPLICABLE
                                : UserProductSearchFilterState.VALUE,
                        durableAttributes.isEmpty()
                                ? List.of()
                                : List.of(new UserProductSearchQualificationPlan.Attribute(
                                        UserProductSearchAttributeName.SIZE, List.of("10")))),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null, null),
                new UserProductSearchQualificationPlan.PriceTierFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                durableAttributes
        );
    }

    private UserProductSearchQualificationPlan withSchemaVersion(
            UserProductSearchQualificationPlan plan,
            int schemaVersion
    ) {
        return new UserProductSearchQualificationPlan(
                schemaVersion,
                plan.effectiveQuery(),
                plan.assistantMessage(),
                plan.suggestedReplies(),
                plan.questionTargets(),
                plan.available(),
                plan.condition(),
                plan.shipsTo(),
                plan.shipsFrom(),
                plan.price(),
                plan.shops(),
                plan.categories(),
                plan.attributes(),
                plan.rating(),
                plan.priceTier(),
                plan.durableAttributes()
        );
    }

    private UserProductSearchQualificationSnapshot snapshot(
            UUID id,
            PersistUserProductSearchQualificationCommand command,
            Instant now
    ) {
        return new UserProductSearchQualificationSnapshot(
                id,
                command.userId(),
                command.conversationId(),
                command.merchantId(),
                command.originalQuery(),
                command.status(),
                command.plan(),
                command.model(),
                command.promptVersion(),
                now,
                now
        );
    }

    private static final class FakeUserSettingsService extends UserSettingsService {

        private final UserSettingsResult result;
        private int calls;

        private FakeUserSettingsService(UserSettingsResult result) {
            super(null, null, null, null, null);
            this.result = result;
        }

        @Override
        public UserSettingsResult get(EnsureUserProfileCommand profileCommand) {
            calls++;
            return result;
        }
    }

    private static final class FakeModelService extends UserProductSearchQualificationModelService {

        private final UserProductSearchQualificationModelResult result;
        private int calls;
        private GenerateUserProductSearchQualificationQuery lastQuery;

        private FakeModelService(UserProductSearchQualificationModelResult result) {
            super(null, null, null, null, new UserProductSearchQualificationPlanResolver());
            this.result = result;
        }

        @Override
        public UserProductSearchQualificationModelResult generate(
                GenerateUserProductSearchQualificationQuery query
        ) {
            calls++;
            lastQuery = query;
            return result;
        }
    }

    private static final class FakePreferenceService extends UserProductSearchPreferenceService {

        private final List<UserProductSearchPreferenceResult> stored;
        private int listCalls;
        private int upsertCalls;
        private SaveUserProductSearchPreferencesCommand lastSaved;

        private FakePreferenceService(List<UserProductSearchPreferenceResult> stored) {
            super(null, null);
            this.stored = stored;
        }

        @Override
        public List<UserProductSearchPreferenceResult> list(UUID userId) {
            listCalls++;
            return stored;
        }

        @Override
        public void upsert(SaveUserProductSearchPreferencesCommand command) {
            upsertCalls++;
            lastSaved = command;
        }
    }

    private final class FakePersistenceService extends UserProductSearchQualificationPersistenceService {

        private Optional<UserProductSearchQualificationSnapshot> found = Optional.empty();
        private UserProductSearchQualificationSnapshot ready;
        private PersistUserProductSearchQualificationCommand lastPersistedCommand;
        private int persistCalls;
        private int getReadyCalls;
        private int refreshReadyCalls;

        private FakePersistenceService() {
            super(null, null, null);
        }

        @Override
        public Optional<UserProductSearchQualificationSnapshot> find(
                GetUserProductSearchQualificationQuery query
        ) {
            return found;
        }

        @Override
        public UserProductSearchQualificationSnapshot persist(
                PersistUserProductSearchQualificationCommand command
        ) {
            persistCalls++;
            lastPersistedCommand = command;
            return snapshot(
                    command.qualificationId(),
                    command,
                    Instant.parse("2026-07-17T10:00:00Z")
            );
        }

        @Override
        public UserProductSearchQualificationSnapshot getReady(
                GetUserProductSearchQualificationQuery query
        ) {
            getReadyCalls++;
            return ready;
        }

        @Override
        public UserProductSearchQualificationSnapshot refreshReady(
                GetUserProductSearchQualificationQuery query
        ) {
            refreshReadyCalls++;
            return found.orElseThrow();
        }
    }
}
