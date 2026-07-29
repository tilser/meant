package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserProductSearchConversationMessage;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserProductSearchCategoryPolicyTest {

    private final UserProductSearchCategoryPolicy policy = new UserProductSearchCategoryPolicy();

    @Test
    void footwearRequiresSizeAndDestinationWhenNeitherIsKnown() {
        var result = policy.enforce(plan("football boots"), query(
                "football boots", "football boots", settings(null), List.of()));

        assertThat(result.missingTargets()).containsExactlyInAnyOrder(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.SHIPS_TO
        );
        assertThat(result.assistantMessage()).contains("shoe size", "ship to");
    }

    @Test
    void runningShoesRequireShoeSizeAndDestination() {
        var result = policy.enforce(plan("cool running shoes"), query(
                "cool running shoes", "cool running shoes", settings(null), List.of()));

        assertThat(result.missingTargets()).containsExactlyInAnyOrder(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.SHIPS_TO
        );
        assertThat(result.assistantMessage()).contains(
                "shoe size",
                "what country should it ship to",
                "region or postal code"
        );
    }

    @Test
    void trustedSimilarityAnchorSuppliesCategoryWithoutBecomingUserFilterEvidence() {
        GenerateUserProductSearchQualificationQuery query =
                new GenerateUserProductSearchQualificationQuery(
                        "find similar products",
                        "find similar products",
                        null,
                        settings(null),
                        List.of(),
                        List.of(),
                        new UserTasteProfileResult(null, List.of(), List.of()),
                        "Cool Running Shoes"
                );

        var result = policy.enforce(plan("find similar products"), query);

        assertThat(result.missingTargets()).containsExactlyInAnyOrder(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.SHIPS_TO
        );
        assertThat(result.assistantMessage()).contains("shoe size", "ship to");
        assertThat(policy.unverifiedHardConstraintTargets(query)).isEmpty();
        assertThat(result.effectiveQuery()).isEqualTo("find similar products");
    }

    @Test
    void exactSimilarityAnchorGovernsCategoryOverAMismatchedDescriptivePhrase() {
        GenerateUserProductSearchQualificationQuery query =
                new GenerateUserProductSearchQualificationQuery(
                        "find something with the look of this black jacket",
                        "find something with the look of this black jacket",
                        null,
                        settings(null),
                        List.of(),
                        List.of(),
                        new UserTasteProfileResult(null, List.of(), List.of()),
                        "Cool Running Shoes"
                );

        var result = policy.enforce(
                plan("find something with the look of this black jacket"),
                query
        );

        assertThat(result.missingTargets()).containsExactlyInAnyOrder(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.SHIPS_TO
        );
        assertThat(result.assistantMessage()).contains("shoe size").doesNotContain("clothing size");
        assertThat(result.effectiveQuery())
                .isEqualTo("find something with the look of this black jacket");
        assertThat(policy.unverifiedHardConstraintTargets(query))
                .containsExactly(UserProductSearchQuestionTarget.COLOR);
    }

    @Test
    void trustedSimilarityAnchorScopesDurableSizeWithoutUsingDescriptiveCategoryWords() {
        GenerateUserProductSearchQualificationQuery query =
                new GenerateUserProductSearchQualificationQuery(
                        "find something with the look of this black jacket",
                        "find something with the look of this black jacket",
                        null,
                        settings(null),
                        List.of(
                                new UserProductSearchPreferenceResult(
                                        "black-jacket",
                                        UserProductSearchAttributeName.SIZE,
                                        List.of("XL")
                                ),
                                new UserProductSearchPreferenceResult(
                                        "running-shoes",
                                        UserProductSearchAttributeName.SIZE,
                                        List.of("46")
                                )
                        ),
                        List.of(),
                        new UserTasteProfileResult(null, List.of(), List.of()),
                        "Cool Running Shoes"
                );

        var result = policy.enforce(
                plan("find something with the look of this black jacket"),
                query
        );

        assertThat(attribute(result, UserProductSearchAttributeName.SIZE).values())
                .containsExactly("46");
        assertThat(result.missingTargets())
                .containsExactly(UserProductSearchQuestionTarget.SHIPS_TO);
    }

    @Test
    void fragmentaryTurnUsesLatestPriorUserProductCategory() {
        GenerateUserProductSearchQualificationQuery query = new GenerateUserProductSearchQualificationQuery(
                "46",
                "Actually size 46",
                null,
                settings(null),
                List.of(),
                List.of(
                        new UserProductSearchConversationMessage(
                                UserProductSearchConversationMessage.Role.USER,
                                "cool running shoes"
                        ),
                        new UserProductSearchConversationMessage(
                                UserProductSearchConversationMessage.Role.ASSISTANT,
                                "What shoe size do you need?"
                        ),
                        new UserProductSearchConversationMessage(
                                UserProductSearchConversationMessage.Role.USER,
                                "Actually size 46"
                        )
                )
        );

        var result = policy.enforce(plan("cool running shoes"), query);

        assertThat(result.missingTargets()).containsExactlyInAnyOrder(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.SHIPS_TO
        );
        assertThat(result.assistantMessage()).contains("shoe size");
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
    void jacketRequiresClothingSizeAndAnExplicitShippingDecisionBeforeSearch() {
        var result = policy.enforce(plan("black jacket"), query(
                "black jacket", "black jacket", settings(null), List.of()));

        assertThat(result.shipsTo().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(attribute(result, UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(result.missingTargets()).containsExactlyInAnyOrder(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.SHIPS_TO
        );
        assertThat(result.assistantMessage()).contains(
                "clothing size",
                "what country should it ship to",
                "region or postal code"
        );
    }

    @Test
    void physicalProductUsesTheSavedDestinationWithoutAskingAgain() {
        UserLocationResult location = new UserLocationResult(
                "saved-home", "Czech Republic", "CZ", "Prague", "18600", "Prague", "Prague");

        var result = policy.enforce(plan("black jacket"), query(
                "black jacket",
                "black jacket",
                settings(location),
                List.of(new UserProductSearchPreferenceResult(
                        "black-jacket", UserProductSearchAttributeName.SIZE, List.of("XL")))
        ));

        assertThat(result.missingTargets()).isEmpty();
        assertThat(result.shipsTo().state()).isEqualTo(UserProductSearchFilterState.VALUE);
        assertThat(result.shipsTo().value())
                .isEqualTo(new UserProductSearchQualificationPlan.Location("CZ", "Prague", "18600"));
        assertThat(attribute(result, UserProductSearchAttributeName.SIZE).values()).containsExactly("XL");
    }

    @Test
    void explicitAnySizeAndDestinationRemainResolvedForApparel() {
        UserProductSearchQualificationPlan candidate = withAnySizeAndDestination(plan("jacket"));

        var result = policy.enforce(candidate, query(
                "jacket",
                "I don't care about size or delivery destination",
                settings(null),
                List.of()
        ));

        assertThat(result.missingTargets()).isEmpty();
        assertThat(result.shipsTo().state()).isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(attribute(result, UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.ANY);
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
                "digital football coaching guide",
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
    void footwearAccessoriesDoNotTriggerWearableSizeQuestions() {
        for (String request : List.of("shoe rack", "shoe cleaner", "boot dryer")) {
            var result = policy.enforce(plan(request), query(
                    request, request, settings(null), List.of()));

            assertThat(attribute(result, UserProductSearchAttributeName.SIZE).state())
                    .as(request)
                    .isNotEqualTo(UserProductSearchFilterState.MISSING);
            assertThat(result.missingTargets())
                    .as(request)
                    .containsExactly(UserProductSearchQuestionTarget.SHIPS_TO);
        }
    }

    @Test
    void ebookReaderRemainsAPhysicalTechnologySearch() {
        var result = policy.enforce(plan("ebook reader"), query(
                "ebook reader", "ebook reader", settings(null), List.of()));

        assertThat(result.shipsTo().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(attribute(result, UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(result.missingTargets()).containsExactly(UserProductSearchQuestionTarget.SHIPS_TO);
    }

    @Test
    void aSoftwareThemedTShirtIsNotReclassifiedAsDigital() {
        var result = policy.enforce(plan("software t-shirt"), query(
                "software t-shirt", "software t-shirt", settings(null), List.of()));

        assertThat(result.shipsTo().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(attribute(result, UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(result.missingTargets()).containsExactlyInAnyOrder(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.SHIPS_TO
        );
    }

    @Test
    void providerContextExcludesFoodPreferencesFromFootwearSearchesButKeepsThemForFood() {
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

    @Test
    void fallbackPolicyAllowsOtherCategoriesAndIdentifiesConstraintsThatNeedConfirmation() {
        GenerateUserProductSearchQualificationQuery query = query(
                "a black handcrafted thingamajig under $50",
                "a black handcrafted thingamajig under $50",
                settings(null),
                List.of()
        );

        assertThat(policy.permitsConservativeFallback(query)).isTrue();
        assertThat(policy.conservativeFallbackDenialReason(query)).isNull();
        assertThat(policy.unverifiedHardConstraintTargets(query)).containsExactly(
                UserProductSearchQuestionTarget.PRICE,
                UserProductSearchQuestionTarget.COLOR
        );
    }

    @Test
    void blackJacketFallbackMustConfirmColorAlongsideServerOwnedCriticalGaps() {
        GenerateUserProductSearchQualificationQuery query = query(
                "black jacket",
                "black jacket",
                settings(null),
                List.of()
        );

        assertThat(policy.unverifiedHardConstraintTargets(query))
                .containsExactly(UserProductSearchQuestionTarget.COLOR);
        UserProductSearchQualificationPlan enforced =
                policy.enforceConservativeFallback(plan("black jacket"), query);
        assertThat(enforced.missingTargets()).containsExactlyInAnyOrder(
                UserProductSearchQuestionTarget.SHIPS_TO,
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.COLOR
        );
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

    private UserProductSearchQualificationPlan withAnySizeAndDestination(
            UserProductSearchQualificationPlan plan
    ) {
        var userAny = new UserProductSearchQualificationPlan.Provenance(
                com.meant.api.module.user.constant.UserProductSearchDecisionSource.CURRENT_USER_TURN,
                "I don't care about size or delivery destination"
        );
        return new UserProductSearchQualificationPlan(
                plan.schemaVersion(),
                plan.effectiveQuery(),
                "Ready",
                List.of(),
                List.of(),
                plan.available(),
                plan.condition(),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.ANY, null, userAny),
                plan.shipsFrom(),
                plan.price(),
                plan.shops(),
                plan.categories(),
                new UserProductSearchQualificationPlan.AttributesFilter(
                        UserProductSearchFilterState.ANY,
                        List.of(
                                notApplicable(UserProductSearchAttributeName.COLOR),
                                new UserProductSearchQualificationPlan.Attribute(
                                        UserProductSearchAttributeName.SIZE,
                                        UserProductSearchFilterState.ANY,
                                        List.of(),
                                        userAny
                                ),
                                notApplicable(UserProductSearchAttributeName.TARGET_GENDER)
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
