package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserProductSearchCategoryPolicyTest {

    private final UserProductSearchCategoryPolicy policy = new UserProductSearchCategoryPolicy();

    @Test
    void footballBootsRequireSizeAndDestinationWhenNeitherIsKnown() {
        var result = policy.enforce(plan("football boots"), query(
                "football boots", "football boots", settings(null), List.of()));

        assertThat(result.missingTargets()).containsExactlyInAnyOrder(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.SHIPS_TO
        );
        assertThat(result.assistantMessage()).contains("boot size", "ship to");
    }

    @Test
    void footballBootsReuseDurableScopedSizeAndSavedDestination() {
        UserLocationResult location = new UserLocationResult(
                "saved-home", "United States", "US", "NY", "10001", "New York", "New York");
        var result = policy.enforce(plan("football boots"), query(
                "football boots",
                "football boots",
                settings(location),
                List.of(new UserProductSearchPreferenceResult(
                        "football-boots", UserProductSearchAttributeName.SIZE, List.of("10")))
        ));

        assertThat(result.missingTargets()).isEmpty();
        assertThat(attribute(result, UserProductSearchAttributeName.SIZE)).satisfies(size -> {
            assertThat(size.state()).isEqualTo(UserProductSearchFilterState.VALUE);
            assertThat(size.values()).containsExactly("10");
        });
        assertThat(result.shipsTo().value())
                .isEqualTo(new UserProductSearchQualificationPlan.Location("US", "NY", "10001"));
    }

    @Test
    void physicalProductRequiresAnExplicitShippingDecisionBeforeSearch() {
        var result = policy.enforce(plan("black jacket"), query(
                "black jacket", "black jacket", settings(null), List.of()));

        assertThat(result.shipsTo().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(result.missingTargets()).containsExactly(UserProductSearchQuestionTarget.SHIPS_TO);
        assertThat(result.assistantMessage()).contains("country or postal code", "I don’t care");
    }

    @Test
    void physicalProductUsesTheSavedDestinationWithoutAskingAgain() {
        UserLocationResult location = new UserLocationResult(
                "saved-home", "Czech Republic", "CZ", "Prague", "18600", "Prague", "Prague");

        var result = policy.enforce(plan("black jacket"), query(
                "black jacket", "black jacket", settings(location), List.of()));

        assertThat(result.missingTargets()).isEmpty();
        assertThat(result.shipsTo().state()).isEqualTo(UserProductSearchFilterState.VALUE);
        assertThat(result.shipsTo().value())
                .isEqualTo(new UserProductSearchQualificationPlan.Location("CZ", "Prague", "18600"));
    }

    @Test
    void foodNeverCarriesSizeOrTargetGenderRequirements() {
        UserProductSearchQualificationPlan candidate = withMissingFitAttributes(plan("gluten-free pasta"));

        var result = policy.enforce(candidate, query(
                "gluten-free pasta", "gluten-free pasta", settings(null), List.of()));

        assertThat(attribute(result, UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(attribute(result, UserProductSearchAttributeName.TARGET_GENDER).state())
                .isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(result.missingTargets()).doesNotContain(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.TARGET_GENDER
        );
    }

    @Test
    void digitalReclassificationClearsObsoleteBootSizeAndShippingRequirements() {
        UserProductSearchQualificationPlan previous = policy.enforce(
                plan("football boots"),
                query("football boots", "football boots", settings(null), List.of())
        );
        UserProductSearchQualificationPlan candidate = plan("digital football coaching guide");
        var resolver = new UserProductSearchQualificationPlanResolver(policy);

        var resolution = resolver.resolve(candidate, query(
                "football boots",
                "Actually make it a digital football coaching guide instead",
                settings(null),
                List.of(),
                previous
        ));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().missingTargets()).isEmpty();
        assertThat(resolution.plan().shipsTo().state()).isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
    }

    @Test
    void providerContextExcludesFoodPreferencesFromBootSearchesButKeepsThemForFood() {
        List<ShoppingFilterResult> filters = List.of(
                new ShoppingFilterResult(
                        "halal", "Halal", "Require products labeled halal.", "food", "require", 1),
                new ShoppingFilterResult(
                        "natural-materials", "Natural", "Prefer natural materials.", "materials", "prefer", 2),
                new ShoppingFilterResult(
                        "highly-rated", "Highly rated", "Prefer strong reviews.", "shopping", "prefer", 3)
        );

        assertThat(policy.providerContextFilters("football boots", filters))
                .extracting(ShoppingFilterResult::id)
                .containsExactly("natural-materials", "highly-rated");
        assertThat(policy.providerContextFilters("pasta", filters))
                .extracting(ShoppingFilterResult::id)
                .containsExactly("halal", "highly-rated");
    }

    private GenerateUserProductSearchQualificationQuery query(
            String original,
            String message,
            UserSettingsResult settings,
            List<UserProductSearchPreferenceResult> preferences
    ) {
        return query(original, message, settings, preferences, null);
    }

    private GenerateUserProductSearchQualificationQuery query(
            String original,
            String message,
            UserSettingsResult settings,
            List<UserProductSearchPreferenceResult> preferences,
            UserProductSearchQualificationPlan previous
    ) {
        return new GenerateUserProductSearchQualificationQuery(
                original, message, previous, settings, preferences);
    }

    private UserSettingsResult settings(UserLocationResult location) {
        List<UserLocationResult> locations = location == null ? List.of() : List.of(location);
        return new UserSettingsResult(
                null,
                null,
                location,
                locations,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private UserProductSearchQualificationPlan plan(String query) {
        var none = UserProductSearchQualificationPlan.Provenance.none();
        return new UserProductSearchQualificationPlan(
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION,
                query,
                "Ready",
                List.of(),
                List.of(),
                new UserProductSearchQualificationPlan.AvailableFilter(
                        UserProductSearchFilterState.VALUE,
                        true,
                        UserProductSearchQualificationPlan.Provenance.system("sale-ready products only")
                ),
                new UserProductSearchQualificationPlan.ConditionFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of(), none),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null, none),
                new UserProductSearchQualificationPlan.LocationsFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of(), none),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null, null, none),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of(),
                        UserProductSearchQualificationPlan.Provenance.system("trusted shop resolver unavailable")),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of(),
                        UserProductSearchQualificationPlan.Provenance.system("trusted taxonomy resolver unavailable")),
                new UserProductSearchQualificationPlan.AttributesFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE,
                        List.of(
                                notApplicable(UserProductSearchAttributeName.COLOR),
                                notApplicable(UserProductSearchAttributeName.SIZE),
                                notApplicable(UserProductSearchAttributeName.TARGET_GENDER)
                        )
                ),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null, null, none),
                new UserProductSearchQualificationPlan.PriceTierFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of(), none),
                List.of()
        );
    }

    private UserProductSearchQualificationPlan withMissingFitAttributes(
            UserProductSearchQualificationPlan plan
    ) {
        return new UserProductSearchQualificationPlan(
                plan.schemaVersion(),
                plan.effectiveQuery(),
                "What size and target gender?",
                plan.suggestedReplies(),
                List.of(UserProductSearchQuestionTarget.SIZE, UserProductSearchQuestionTarget.TARGET_GENDER),
                plan.available(),
                plan.condition(),
                plan.shipsTo(),
                plan.shipsFrom(),
                plan.price(),
                plan.shops(),
                plan.categories(),
                new UserProductSearchQualificationPlan.AttributesFilter(
                        UserProductSearchFilterState.MISSING,
                        List.of(
                                notApplicable(UserProductSearchAttributeName.COLOR),
                                missing(UserProductSearchAttributeName.SIZE),
                                missing(UserProductSearchAttributeName.TARGET_GENDER)
                        )
                ),
                plan.rating(),
                plan.priceTier(),
                plan.durableAttributes()
        );
    }

    private UserProductSearchQualificationPlan.Attribute attribute(
            UserProductSearchQualificationPlan plan,
            UserProductSearchAttributeName name
    ) {
        return plan.attributes().values().stream()
                .filter(attribute -> attribute.name() == name)
                .findFirst()
                .orElseThrow();
    }

    private UserProductSearchQualificationPlan.Attribute notApplicable(
            UserProductSearchAttributeName name
    ) {
        return new UserProductSearchQualificationPlan.Attribute(
                name, UserProductSearchFilterState.NOT_APPLICABLE, List.of(),
                UserProductSearchQualificationPlan.Provenance.none());
    }

    private UserProductSearchQualificationPlan.Attribute missing(UserProductSearchAttributeName name) {
        return new UserProductSearchQualificationPlan.Attribute(
                name, UserProductSearchFilterState.MISSING, List.of(),
                UserProductSearchQualificationPlan.Provenance.none());
    }
}
