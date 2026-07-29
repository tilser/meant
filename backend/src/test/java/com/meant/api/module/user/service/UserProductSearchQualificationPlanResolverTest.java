package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.module.user.constant.UserProductCondition;
import com.meant.api.module.user.constant.UserProductPriceTier;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.constant.UserTasteSignalStatus;
import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.constant.UserTasteSuggestionStatus;
import com.meant.api.module.user.service.dto.UserProductSearchConversationMessage;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import com.meant.api.module.user.service.dto.UserTasteSignalResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserProductSearchQualificationPlanResolverTest {

    private final UserProductSearchQualificationPlanResolver resolver =
            new UserProductSearchQualificationPlanResolver();

    @Test
    void acceptsHardDecisionEvidenceFromAPriorUserConversationMessage() {
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(),
                new UserProductSearchQualificationPlan.ConditionFilter(
                        UserProductSearchFilterState.VALUE,
                        List.of(UserProductCondition.NEW),
                        conversation("I only buy new products.")
                ),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        var query = new GenerateUserProductSearchQualificationQuery(
                "desk lamp",
                "Find a desk lamp.",
                null,
                settingsWithDestination(),
                List.of(),
                List.of(
                        userMessage("desk lamp"),
                        assistantMessage("Tell me any hard constraints for this desk lamp."),
                        userMessage("I only buy new products."),
                        assistantMessage("Understood."),
                        userMessage("Find a desk lamp.")
                )
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).as(resolution.violations().toString()).isTrue();
        assertThat(resolution.plan().condition()).satisfies(condition -> {
            assertThat(condition.state()).isEqualTo(UserProductSearchFilterState.VALUE);
            assertThat(condition.values()).containsExactly(UserProductCondition.NEW);
            assertThat(condition.provenance().source())
                    .isEqualTo(UserProductSearchDecisionSource.CONVERSATION);
        });
    }

    @Test
    void acceptsExplicitIndifferenceOnlyWhenItAppearsInAPriorUserMessage() {
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        null,
                        conversation("Any budget is fine.")
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        var query = new GenerateUserProductSearchQualificationQuery(
                "desk lamp",
                "Find a desk lamp.",
                null,
                settingsWithDestination(),
                List.of(),
                List.of(
                        userMessage("desk lamp"),
                        assistantMessage("What budget should I use for this desk lamp?"),
                        userMessage("Any budget is fine."),
                        userMessage("Find a desk lamp.")
                )
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).as(resolution.violations().toString()).isTrue();
        assertThat(resolution.plan().price().state()).isEqualTo(UserProductSearchFilterState.ANY);
    }

    @Test
    void rejectsEverySupportedConstraintThatTheModelMarksIrrelevant() {
        record Scenario(String request, UserProductSearchQuestionTarget target) {
        }
        for (Scenario scenario : List.of(
                new Scenario("used desk lamp", UserProductSearchQuestionTarget.CONDITION),
                new Scenario("desk lamp shipped to Canada", UserProductSearchQuestionTarget.SHIPS_TO),
                new Scenario("desk lamp shipped from Canada", UserProductSearchQuestionTarget.SHIPS_FROM),
                new Scenario("black desk lamp", UserProductSearchQuestionTarget.COLOR),
                new Scenario("desk lamp size XL", UserProductSearchQuestionTarget.SIZE),
                new Scenario("women's desk lamp", UserProductSearchQuestionTarget.TARGET_GENDER),
                new Scenario("desk lamp rated 4 stars", UserProductSearchQuestionTarget.RATING),
                new Scenario("desk lamp low price tier", UserProductSearchQuestionTarget.PRICE_TIER)
        )) {
            UserProductSearchQualificationPlan candidate = plan(
                    "desk lamp",
                    List.of(scenario.target()),
                    notApplicableCondition(),
                    notApplicableShipsFrom(),
                    notApplicablePrice(),
                    notApplicableAttributes(),
                    notApplicableRating(),
                    notApplicablePriceTier()
            );

            var resolution = resolver.resolve(candidate, query(
                    scenario.request(),
                    scenario.request(),
                    null,
                    settingsWithUsDestination()
            ));

            assertThat(resolution.valid()).as(scenario.request()).isFalse();
            assertThat(decisionState(resolution.plan(), scenario.target()))
                    .as(scenario.request())
                    .isEqualTo(UserProductSearchFilterState.MISSING);
            assertThat(resolution.violations())
                    .as(scenario.request())
                    .contains(scenario.target()
                            + " cannot drop or override an explicit active-request decision");
        }
    }

    @Test
    void deterministicallyAppliesEveryTargetSpecificIndifferenceWhenTheModelDropsIt() {
        record Scenario(String request, UserProductSearchQuestionTarget target) {
        }
        for (Scenario scenario : List.of(
                new Scenario("desk lamp, condition doesn't matter", UserProductSearchQuestionTarget.CONDITION),
                new Scenario("desk lamp, location doesn't matter", UserProductSearchQuestionTarget.SHIPS_TO),
                new Scenario("desk lamp, shipping origin doesn't matter", UserProductSearchQuestionTarget.SHIPS_FROM),
                new Scenario("desk lamp, budget doesn't matter", UserProductSearchQuestionTarget.PRICE),
                new Scenario("desk lamp, color doesn't matter", UserProductSearchQuestionTarget.COLOR),
                new Scenario("desk lamp, size doesn't matter", UserProductSearchQuestionTarget.SIZE),
                new Scenario("desk lamp, target gender doesn't matter",
                        UserProductSearchQuestionTarget.TARGET_GENDER),
                new Scenario("desk lamp, rating doesn't matter", UserProductSearchQuestionTarget.RATING),
                new Scenario("desk lamp, price tier doesn't matter", UserProductSearchQuestionTarget.PRICE_TIER)
        )) {
            UserProductSearchQualificationPlan candidate = plan(
                    "desk lamp",
                    List.of(),
                    notApplicableCondition(),
                    notApplicableShipsFrom(),
                    notApplicablePrice(),
                    notApplicableAttributes(),
                    notApplicableRating(),
                    notApplicablePriceTier()
            );

            var resolution = resolver.resolve(candidate, query(
                    scenario.request(),
                    scenario.request(),
                    null,
                    settingsWithUsDestination()
            ));

            assertThat(resolution.valid()).as(scenario.request()).isTrue();
            assertThat(decisionState(resolution.plan(), scenario.target()))
                    .as(scenario.request())
                    .isEqualTo(UserProductSearchFilterState.ANY);
            assertThat(resolution.plan().explicitAnyTargets()).containsExactly(scenario.target());
            assertThat(resolution.plan().profileSuppressionTargets()).containsExactly(scenario.target());
        }
    }

    @Test
    void explicitLocationIndifferenceSuppressesTheSavedDestination() {
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "desk lamp, location doesn't matter",
                "desk lamp, location doesn't matter",
                null,
                settingsWithUsDestination()
        ));

        assertThat(resolution.valid()).as(resolution.violations().toString()).isTrue();
        assertThat(resolution.plan().shipsTo().state()).isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(resolution.plan().shipsTo().value()).isNull();
        assertThat(resolution.plan().profileSuppressionTargets())
                .contains(UserProductSearchQuestionTarget.SHIPS_TO);
    }

    @Test
    void explicitCurrentLocationCannotBeReplacedByTheSavedDestination() {
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(UserProductSearchQuestionTarget.SHIPS_TO),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "desk lamp, I'm based in Canada",
                "desk lamp, I'm based in Canada",
                null,
                settingsWithUsDestination()
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.plan().shipsTo().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(resolution.plan().shipsTo().value()).isNull();
        assertThat(resolution.violations())
                .contains("SHIPS_TO cannot drop or override an explicit active-request decision");
    }

    @Test
    void oldProductSizeCannotGroundTheActiveProductFamily() {
        UserProductSearchQualificationPlan candidate = plan(
                "running shoes",
                List.of(UserProductSearchQuestionTarget.SIZE),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                attributes(
                        notApplicableAttribute(UserProductSearchAttributeName.COLOR),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.SIZE,
                                UserProductSearchFilterState.VALUE,
                                List.of("XL"),
                                conversation("XL")
                        ),
                        notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
                ),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        var query = new GenerateUserProductSearchQualificationQuery(
                "running shoes",
                "running shoes",
                null,
                settingsWithUsDestination(),
                List.of(),
                List.of(
                        userMessage("My jacket size is XL."),
                        assistantMessage("Understood."),
                        userMessage("running shoes")
                )
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).isFalse();
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(resolution.violations())
                .contains("SIZE provenance evidence does not match its claimed source");
    }

    @Test
    void oldProductAdjectivesCannotEnterTheActiveEffectiveQuery() {
        UserProductSearchQualificationPlan candidate = plan(
                "red running shoes",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        var query = new GenerateUserProductSearchQualificationQuery(
                "running shoes",
                "running shoes",
                null,
                settingsWithUsDestination(),
                List.of(),
                List.of(
                        userMessage("red luxury jacket"),
                        assistantMessage("Here are jackets."),
                        userMessage("running shoes")
                )
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .anyMatch(violation -> violation.contains("effectiveQuery contains unsupported terms")
                        && violation.contains("red"));
        assertThat(resolution.plan().effectiveQuery()).isEqualTo("running shoes");
    }

    @Test
    void newBalanceDoesNotBecomeAConditionConstraint() {
        UserProductSearchQualificationPlan candidate = plan(
                "New Balance running shoes",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "New Balance running shoes",
                "New Balance running shoes",
                null,
                settingsWithUsDestination()
        ));

        assertThat(resolution.plan().condition().state())
                .isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(resolution.violations())
                .noneMatch(violation -> violation.startsWith("CONDITION cannot drop"));

        UserProductSearchQualificationPlan misclassified = plan(
                "New Balance running shoes",
                List.of(UserProductSearchQuestionTarget.CONDITION),
                new UserProductSearchQualificationPlan.ConditionFilter(
                        UserProductSearchFilterState.VALUE,
                        List.of(UserProductCondition.NEW),
                        originalQuery("New Balance")
                ),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var rejected = resolver.resolve(misclassified, query(
                "New Balance running shoes",
                "New Balance running shoes",
                null,
                settingsWithUsDestination()
        ));

        assertThat(rejected.plan().condition().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(rejected.violations())
                .contains("CONDITION provenance evidence does not support its typed value");
    }

    @Test
    void rejectsConversationProvenanceFromAssistantTextOrTheLatestUserTurn() {
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(UserProductSearchQuestionTarget.CONDITION),
                new UserProductSearchQualificationPlan.ConditionFilter(
                        UserProductSearchFilterState.VALUE,
                        List.of(UserProductCondition.NEW),
                        conversation("I only buy new products.")
                ),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        var assistantOnly = new GenerateUserProductSearchQualificationQuery(
                "desk lamp",
                "Find a desk lamp.",
                null,
                settingsWithDestination(),
                List.of(),
                List.of(
                        assistantMessage("I only buy new products."),
                        userMessage("Find a desk lamp.")
                )
        );
        var latestUserTurn = new GenerateUserProductSearchQualificationQuery(
                "desk lamp",
                "I only buy new products.",
                null,
                settingsWithDestination(),
                List.of(),
                List.of(userMessage("I only buy new products."))
        );

        var assistantResolution = resolver.resolve(candidate, assistantOnly);
        var currentResolution = resolver.resolve(candidate, latestUserTurn);

        assertThat(assistantResolution.valid()).isFalse();
        assertThat(currentResolution.valid()).isFalse();
        assertThat(assistantResolution.violations())
                .contains("CONDITION provenance evidence does not match its claimed source");
        assertThat(currentResolution.violations())
                .contains("CONDITION provenance evidence does not match its claimed source");
    }

    @Test
    void rejectsAnyWhoseEvidenceDoesNotExpressFilterSpecificIndifference() {
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(UserProductSearchQuestionTarget.RATING),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        null,
                        originalQuery("desk lamp")
                ),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "desk lamp", "desk lamp", null, settingsWithDestination()));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations()).contains("RATING ANY lacks explicit filter-specific indifference");
        assertThat(resolution.plan().rating().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(resolution.plan().missingTargets()).containsExactly(UserProductSearchQuestionTarget.RATING);
        assertThat(resolver.safeFallback(resolution.plan()).questionTargets())
                .containsExactly(UserProductSearchQuestionTarget.RATING);
    }

    @Test
    void doesNotReuseAStoredSizeFromAnUnrelatedProductScope() {
        UserProductSearchQualificationPlan candidate = plan(
                "running shoes",
                List.of(UserProductSearchQuestionTarget.SIZE),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                attributes(
                        notApplicableAttribute(UserProductSearchAttributeName.COLOR),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.SIZE,
                                UserProductSearchFilterState.VALUE,
                                List.of("32"),
                                new UserProductSearchQualificationPlan.Provenance(
                                        UserProductSearchDecisionSource.DURABLE_PREFERENCE,
                                        "jeans SIZE 32"
                                )
                        ),
                        notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
                ),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        GenerateUserProductSearchQualificationQuery query = new GenerateUserProductSearchQualificationQuery(
                "running shoes",
                "running shoes",
                null,
                settingsWithDestination(),
                List.of(new UserProductSearchPreferenceResult(
                        "jeans", UserProductSearchAttributeName.SIZE, List.of("32")))
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("SIZE provenance evidence does not match its claimed source");
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(resolution.plan().questionTargets()).containsExactly(UserProductSearchQuestionTarget.SIZE);
    }

    @Test
    void reusesAStoredSizeFromTheSameProductScope() {
        UserProductSearchQualificationPlan candidate = plan(
                "running shoes",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                attributes(
                        notApplicableAttribute(UserProductSearchAttributeName.COLOR),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.SIZE,
                                UserProductSearchFilterState.VALUE,
                                List.of("10"),
                                new UserProductSearchQualificationPlan.Provenance(
                                        UserProductSearchDecisionSource.DURABLE_PREFERENCE,
                                        "running-shoes SIZE 10"
                                )
                        ),
                        notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
                ),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        GenerateUserProductSearchQualificationQuery query = new GenerateUserProductSearchQualificationQuery(
                "running shoes",
                "running shoes",
                null,
                settingsWithDestination(),
                List.of(new UserProductSearchPreferenceResult(
                        "running-shoes", UserProductSearchAttributeName.SIZE, List.of("10")))
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).isTrue();
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.SIZE)).satisfies(size -> {
            assertThat(size.state()).isEqualTo(UserProductSearchFilterState.VALUE);
            assertThat(size.values()).containsExactly("10");
            assertThat(size.provenance().source())
                    .isEqualTo(UserProductSearchDecisionSource.DURABLE_PREFERENCE);
        });
        assertThat(resolution.plan().missingTargets()).isEmpty();
    }

    @Test
    void rejectsATypedProfileValueThatItsEvidenceDoesNotSupport() {
        UserProductSearchQualificationPlan candidate = plan(
                "jeans",
                List.of(UserProductSearchQuestionTarget.TARGET_GENDER),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                attributes(
                        notApplicableAttribute(UserProductSearchAttributeName.COLOR),
                        notApplicableAttribute(UserProductSearchAttributeName.SIZE),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.TARGET_GENDER,
                                UserProductSearchFilterState.VALUE,
                                List.of("Female"),
                                new UserProductSearchQualificationPlan.Provenance(
                                        UserProductSearchDecisionSource.PROFILE,
                                        "men"
                                )
                        )
                ),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        UserSettingsResult settings = settings();
        UserSettingsResult mensSettings = new UserSettingsResult(
                settings.budget(),
                "men",
                settings.location(),
                settings.locations(),
                settings.filters(),
                settings.availableFilters(),
                settings.parsedFilterIds(),
                settings.unmappedPreferences(),
                settings.createdAt(),
                settings.updatedAt()
        );
        GenerateUserProductSearchQualificationQuery query = new GenerateUserProductSearchQualificationQuery(
                "jeans", "jeans", null, mensSettings, List.of());

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("TARGET_GENDER provenance evidence does not support its typed value");
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.TARGET_GENDER).state())
                .isEqualTo(UserProductSearchFilterState.MISSING);
    }

    @Test
    void rejectsShipsToPostalCodeThatIsMissingFromTheClaimedEvidence() {
        UserProductSearchQualificationPlan candidate = withShipsTo(
                plan(
                        "running shoes",
                        List.of(UserProductSearchQuestionTarget.SHIPS_TO),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        notApplicableAttributes(),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.VALUE,
                        new UserProductSearchQualificationPlan.Location("US", null, "90210"),
                        originalQuery("shipped to US")
                )
        );

        var resolution = resolver.resolve(candidate, query(
                "running shoes shipped to US",
                "running shoes shipped to US",
                null
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("SHIPS_TO region or postal code lacks provenance evidence");
        assertThat(resolution.plan().shipsTo().state()).isEqualTo(UserProductSearchFilterState.MISSING);
    }

    @Test
    void enrichesProfileShipsToWithSavedUcpRegionAndPostalCode() {
        UserProductSearchQualificationPlan candidate = withShipsTo(
                plan(
                        "running shoes",
                        List.of(),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        notApplicableAttributes(),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.VALUE,
                        new UserProductSearchQualificationPlan.Location("US", null, null),
                        new UserProductSearchQualificationPlan.Provenance(
                                UserProductSearchDecisionSource.PROFILE,
                                "United States US"
                        )
                )
        );
        UserLocationResult saved = new UserLocationResult(
                "geonames:5128581",
                "United States",
                "US",
                "NY",
                "10001",
                "New York",
                "New York"
        );
        UserSettingsResult base = settings();
        UserSettingsResult settings = new UserSettingsResult(
                base.budget(),
                base.clothingFit(),
                saved,
                List.of(saved),
                base.filters(),
                base.availableFilters(),
                base.parsedFilterIds(),
                base.unmappedPreferences(),
                base.createdAt(),
                base.updatedAt()
        );
        GenerateUserProductSearchQualificationQuery query = new GenerateUserProductSearchQualificationQuery(
                "running shoes",
                "running shoes",
                null,
                settings,
                List.of()
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().shipsTo().value())
                .isEqualTo(new UserProductSearchQualificationPlan.Location("US", "NY", "10001"));
    }

    @Test
    void ignoresAnUnmatchedModelProfileClaimAndUsesThePrimarySavedDestination() {
        UserProductSearchQualificationPlan candidate = withShipsTo(
                plan(
                        "black jacket",
                        List.of(),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        blackAttributes(),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.VALUE,
                        new UserProductSearchQualificationPlan.Location("CA", null, null),
                        new UserProductSearchQualificationPlan.Provenance(
                                UserProductSearchDecisionSource.PROFILE,
                                "Canada"
                        )
                )
        );

        var resolution = resolver.resolve(candidate, query(
                "black jacket", "black jacket", null, settingsWithDestination()));

        assertThat(resolution.valid()).as(resolution.violations().toString()).isTrue();
        assertThat(resolution.plan().shipsTo().value())
                .isEqualTo(new UserProductSearchQualificationPlan.Location("CZ", "Prague", "18600"));
        assertThat(resolution.plan().shipsTo().provenance().source())
                .isEqualTo(UserProductSearchDecisionSource.PROFILE);
    }

    @Test
    void rejectsASecondarySavedProfileDestinationAndUsesThePrimaryDestination() {
        UserLocationResult primary = new UserLocationResult(
                "home", "United States", "US", "CA", "94107", "California", "San Francisco");
        UserLocationResult secondary = new UserLocationResult(
                "other", "Canada", "CA", "BC", "V6B 1A1", "British Columbia", "Vancouver");
        UserSettingsResult base = settings();
        UserSettingsResult settings = new UserSettingsResult(
                base.budget(),
                base.clothingFit(),
                primary,
                List.of(primary, secondary),
                base.filters(),
                base.availableFilters(),
                base.parsedFilterIds(),
                base.unmappedPreferences(),
                base.createdAt(),
                base.updatedAt()
        );
        UserProductSearchQualificationPlan candidate = withShipsTo(
                plan(
                        "black jacket",
                        List.of(),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        blackAttributes(),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.VALUE,
                        new UserProductSearchQualificationPlan.Location("CA", "BC", "V6B 1A1"),
                        new UserProductSearchQualificationPlan.Provenance(
                                UserProductSearchDecisionSource.PROFILE,
                                "Canada CA BC V6B 1A1"
                        )
                )
        );

        var resolution = resolver.resolve(candidate, query(
                "black jacket", "black jacket", null, settings));

        assertThat(resolution.valid()).as(resolution.violations().toString()).isTrue();
        assertThat(resolution.plan().shipsTo().value())
                .isEqualTo(new UserProductSearchQualificationPlan.Location("US", "CA", "94107"));
        assertThat(resolution.plan().shipsTo().provenance().source())
                .isEqualTo(UserProductSearchDecisionSource.PROFILE);
    }

    @Test
    void acceptsAnExplicitCurrentTurnDestinationThatDiffersFromThePrimaryProfileLocation() {
        UserLocationResult primary = new UserLocationResult(
                "home", "United States", "US", "CA", "94107", "California", "San Francisco");
        UserLocationResult secondary = new UserLocationResult(
                "other", "Canada", "CA", "BC", "V6B 1A1", "British Columbia", "Vancouver");
        UserSettingsResult base = settings();
        UserSettingsResult settings = new UserSettingsResult(
                base.budget(),
                base.clothingFit(),
                primary,
                List.of(primary, secondary),
                base.filters(),
                base.availableFilters(),
                base.parsedFilterIds(),
                base.unmappedPreferences(),
                base.createdAt(),
                base.updatedAt()
        );
        UserProductSearchQualificationPlan candidate = withShipsTo(
                plan(
                        "black jacket",
                        List.of(),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        blackAttributes(),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.VALUE,
                        new UserProductSearchQualificationPlan.Location("CA", null, null),
                        new UserProductSearchQualificationPlan.Provenance(
                                UserProductSearchDecisionSource.CURRENT_USER_TURN,
                                "Ship it to Canada"
                        )
                )
        );

        var resolution = resolver.resolve(candidate, query(
                "black jacket", "Ship it to Canada", null, settings));

        assertThat(resolution.valid()).as(resolution.violations().toString()).isTrue();
        assertThat(resolution.plan().shipsTo().value())
                .isEqualTo(new UserProductSearchQualificationPlan.Location("CA", null, null));
        assertThat(resolution.plan().shipsTo().provenance().source())
                .isEqualTo(UserProductSearchDecisionSource.CURRENT_USER_TURN);
    }

    @Test
    void acceptsExplicitUserLocationRelationsAsShipsToEvidence() {
        for (String evidence : List.of(
                "I'm based in the United States",
                "I'm located in the United States",
                "I live in the United States",
                "I am in the United States"
        )) {
            String request = "desk lamp for delivery; " + evidence;
            UserProductSearchQualificationPlan candidate = withShipsTo(
                    plan(
                            "desk lamp",
                            List.of(),
                            notApplicableCondition(),
                            notApplicableShipsFrom(),
                            notApplicablePrice(),
                            notApplicableAttributes(),
                            notApplicableRating(),
                            notApplicablePriceTier()
                    ),
                    new UserProductSearchQualificationPlan.LocationFilter(
                            UserProductSearchFilterState.VALUE,
                            new UserProductSearchQualificationPlan.Location("US", null, null),
                            originalQuery(evidence)
                    )
            );

            var resolution = resolver.resolve(candidate, query(
                    request,
                    request,
                    null,
                    settings()
            ));

            assertThat(resolution.valid())
                    .as("%s %s", evidence, resolution.violations())
                    .isTrue();
            assertThat(resolution.plan().shipsTo().value().country()).isEqualTo("US");
        }
    }

    @Test
    void doesNotInferACountryFromACityOnlyLocationStatement() {
        String evidence = "I'm based in San Francisco";
        String request = "desk lamp for delivery; " + evidence;
        UserProductSearchQualificationPlan candidate = withShipsTo(
                plan(
                        "desk lamp",
                        List.of(UserProductSearchQuestionTarget.SHIPS_TO),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        notApplicableAttributes(),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.VALUE,
                        new UserProductSearchQualificationPlan.Location("US", null, null),
                        originalQuery(evidence)
                )
        );

        var resolution = resolver.resolve(candidate, query(
                request,
                request,
                null,
                settings()
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.plan().shipsTo().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(resolution.violations())
                .contains("SHIPS_TO provenance evidence does not support its typed value");
    }

    @Test
    void rejectsUppercaseRegionEvidenceWhenTheRawSourceOnlyContainsTheLowercaseWord() {
        UserProductSearchQualificationPlan candidate = withShipsTo(
                plan(
                        "running shoes",
                        List.of(UserProductSearchQuestionTarget.SHIPS_TO),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        notApplicableAttributes(),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.VALUE,
                        new UserProductSearchQualificationPlan.Location("US", "IN", null),
                        originalQuery("US IN")
                )
        );

        var resolution = resolver.resolve(candidate, query(
                "running shoes shipped to US in black",
                "running shoes shipped to US in black",
                null
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("SHIPS_TO region or postal code lacks provenance evidence");
        assertThat(resolution.plan().shipsTo().state()).isEqualTo(UserProductSearchFilterState.MISSING);
    }

    @Test
    void rejectsShipsFromRegionThatIsMissingFromTheClaimedEvidence() {
        UserProductSearchQualificationPlan candidate = plan(
                "running shoes",
                List.of(UserProductSearchQuestionTarget.SHIPS_FROM),
                notApplicableCondition(),
                new UserProductSearchQualificationPlan.LocationsFilter(
                        UserProductSearchFilterState.VALUE,
                        List.of(new UserProductSearchQualificationPlan.Location("US", "CA", null)),
                        originalQuery("shipped from US")
                ),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "running shoes shipped from US",
                "running shoes shipped from US",
                null
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("SHIPS_FROM region or postal code lacks provenance evidence");
        assertThat(resolution.plan().shipsFrom().state()).isEqualTo(UserProductSearchFilterState.MISSING);
    }

    @Test
    void rejectsPriceWhenDecimalEvidenceOnlyMatchesAfterRemovingTheSeparator() {
        UserProductSearchQualificationPlan candidate = plan(
                "running shoes",
                List.of(UserProductSearchQuestionTarget.PRICE),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.VALUE,
                        null,
                        150_000L,
                        originalQuery("under 15.00 USD")
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "running shoes under 15.00 USD",
                "running shoes under 15.00 USD",
                null
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("PRICE typed bounds do not match the buyer's grounded bound direction and values");
        assertThat(resolution.plan().price().state()).isEqualTo(UserProductSearchFilterState.MISSING);
    }

    @Test
    void rejectsRatingCountWhenDecimalRatingOnlyMatchesAfterRemovingTheSeparator() {
        UserProductSearchQualificationPlan candidate = plan(
                "running shoes",
                List.of(UserProductSearchQuestionTarget.RATING),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.VALUE,
                        null,
                        45L,
                        originalQuery("4.5")
                ),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "running shoes rated 4.5",
                "running shoes rated 4.5",
                null
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("RATING provenance evidence does not support its typed value");
        assertThat(resolution.plan().rating().state()).isEqualTo(UserProductSearchFilterState.MISSING);
    }

    @Test
    void rejectsUppercaseCountryEvidenceWhenTheRawSourceOnlyContainsTheLowercaseWord() {
        UserProductSearchQualificationPlan candidate = plan(
                "made in Italy shoes",
                List.of(UserProductSearchQuestionTarget.SHIPS_FROM),
                notApplicableCondition(),
                new UserProductSearchQualificationPlan.LocationsFilter(
                        UserProductSearchFilterState.VALUE,
                        List.of(new UserProductSearchQualificationPlan.Location("IN", null, null)),
                        originalQuery("IN")
                ),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "made in Italy shoes",
                "made in Italy shoes",
                null
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("SHIPS_FROM provenance evidence does not support its typed value");
        assertThat(resolution.plan().shipsFrom().state()).isEqualTo(UserProductSearchFilterState.MISSING);
    }

    @Test
    void acceptsTheExactDecimalPriceWithoutTreatingItAsASeparatorlessInteger() {
        UserProductSearchQualificationPlan candidate = plan(
                "running shoes",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.VALUE,
                        null,
                        1_500L,
                        originalQuery("under 15.00 USD")
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "running shoes under 15.00 USD",
                "running shoes under 15.00 USD",
                null
        ));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().price().maxUsdMinor()).isEqualTo(1_500L);
    }

    @Test
    void rejectsACurrencyAmbiguousPriceBoundInsteadOfAssumingUsd() {
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(UserProductSearchQuestionTarget.PRICE),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.VALUE,
                        null,
                        10_000L,
                        originalQuery("under 100")
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "desk lamp under 100",
                "desk lamp under 100",
                null,
                settingsWithDestination()
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("PRICE VALUE requires an explicit USD denomination");
        assertThat(resolution.plan().price().state()).isEqualTo(UserProductSearchFilterState.MISSING);
    }

    @Test
    void treatsBareDollarAndGenericDollarBoundsAsCurrencyAmbiguous() {
        for (String request : List.of(
                "desk lamp under $100",
                "desk lamp under 100 dollars"
        )) {
            UserProductSearchQualificationPlan candidate = plan(
                    "desk lamp",
                    List.of(UserProductSearchQuestionTarget.PRICE),
                    notApplicableCondition(),
                    notApplicableShipsFrom(),
                    new UserProductSearchQualificationPlan.PriceFilter(
                            UserProductSearchFilterState.VALUE,
                            null,
                            10_000L,
                            originalQuery(request)
                    ),
                    notApplicableAttributes(),
                    notApplicableRating(),
                    notApplicablePriceTier()
            );

            var resolution = resolver.resolve(candidate, query(
                    request,
                    request,
                    null,
                    settingsWithDestination()
            ));

            assertThat(resolution.valid()).as(request).isFalse();
            assertThat(resolution.violations())
                    .as(request)
                    .contains("PRICE VALUE requires an explicit USD denomination");
            assertThat(resolution.plan().price().state())
                    .as(request)
                    .isEqualTo(UserProductSearchFilterState.MISSING);
        }
    }

    @Test
    void doesNotLetTheModelIgnoreStandaloneDenominatedPriceAmounts() {
        for (String request : List.of(
                "desk lamp $100",
                "desk lamp 100 dollars",
                "desk lamp 100 USD",
                "desk lamp USD 100"
        )) {
            UserProductSearchQualificationPlan candidate = plan(
                    "desk lamp",
                    List.of(UserProductSearchQuestionTarget.PRICE),
                    notApplicableCondition(),
                    notApplicableShipsFrom(),
                    notApplicablePrice(),
                    notApplicableAttributes(),
                    notApplicableRating(),
                    notApplicablePriceTier()
            );

            var resolution = resolver.resolve(candidate, query(
                    request,
                    request,
                    null,
                    settingsWithDestination()
            ));

            assertThat(resolution.valid()).as(request).isFalse();
            assertThat(resolution.plan().price().state())
                    .as(request)
                    .isEqualTo(UserProductSearchFilterState.MISSING);
        }
    }

    @Test
    void rejectsInventedDirectionForAStandaloneUsdAmount() {
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(UserProductSearchQuestionTarget.PRICE),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.VALUE,
                        null,
                        10_000L,
                        originalQuery("100 USD")
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "desk lamp 100 USD",
                "desk lamp 100 USD",
                null,
                settingsWithDestination()
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("PRICE typed bounds do not match the buyer's grounded bound direction and values");
        assertThat(resolution.plan().price().state()).isEqualTo(UserProductSearchFilterState.MISSING);
    }

    @Test
    void doesNotLetTheModelMarkAnUnqualifiedPriceBoundIrrelevant() {
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(UserProductSearchQuestionTarget.PRICE),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "desk lamp under 100",
                "desk lamp under 100",
                null,
                settingsWithDestination()
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("PRICE cannot be irrelevant while a currency-ambiguous bound is present");
        assertThat(resolution.plan().price().state()).isEqualTo(UserProductSearchFilterState.MISSING);
    }

    @Test
    void acceptsAnExplicitUsdPriceBound() {
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.VALUE,
                        null,
                        10_000L,
                        originalQuery("under 100 USD")
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "desk lamp under 100 USD",
                "desk lamp under 100 USD",
                null,
                settingsWithDestination()
        ));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().price().maxUsdMinor()).isEqualTo(10_000L);
    }

    @Test
    void doesNotLetTheModelIgnoreAnExplicitUsdPriceBound() {
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(UserProductSearchQuestionTarget.PRICE),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "desk lamp under 100 U.S. dollars",
                "desk lamp under 100 U.S. dollars",
                null,
                settingsWithDestination()
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("PRICE cannot be irrelevant while an explicit USD bound is present");
        assertThat(resolution.plan().price().state()).isEqualTo(UserProductSearchFilterState.MISSING);
    }

    @Test
    void combinesACurrencyOnlyFollowUpWithTheOriginalBoundAfterACombinedQuestion() {
        UserProductSearchQualificationPlan previous = plan(
                "desk lamp",
                List.of(
                        UserProductSearchQuestionTarget.PRICE,
                        UserProductSearchQuestionTarget.COLOR
                ),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.MISSING,
                        null,
                        null,
                        UserProductSearchQualificationPlan.Provenance.none()
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.VALUE,
                        null,
                        10_000L,
                        currentTurn("USD")
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "desk lamp under 100",
                "USD",
                previous,
                settingsWithDestination()
        ));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().price().maxUsdMinor()).isEqualTo(10_000L);
        assertThat(resolution.plan().price().provenance().source())
                .isEqualTo(UserProductSearchDecisionSource.CURRENT_USER_TURN);
    }

    @Test
    void rejectsAReversedBoundDirectionOnACurrencyOnlyFollowUp() {
        UserProductSearchQualificationPlan previous = plan(
                "desk lamp",
                List.of(UserProductSearchQuestionTarget.PRICE),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.MISSING,
                        null,
                        null,
                        UserProductSearchQualificationPlan.Provenance.none()
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(UserProductSearchQuestionTarget.PRICE),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.VALUE,
                        10_000L,
                        null,
                        currentTurn("USD")
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "desk lamp under 100",
                "USD",
                previous,
                settingsWithDestination()
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("PRICE typed bounds do not match the buyer's grounded bound direction and values");
        assertThat(resolution.plan().price().state()).isEqualTo(UserProductSearchFilterState.MISSING);
    }

    @Test
    void preservesBothOrderedRangeBoundsOnACurrencyOnlyFollowUp() {
        UserProductSearchQualificationPlan previous = plan(
                "desk lamp",
                List.of(UserProductSearchQuestionTarget.PRICE),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.MISSING,
                        null,
                        null,
                        UserProductSearchQualificationPlan.Provenance.none()
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.VALUE,
                        5_000L,
                        10_000L,
                        currentTurn("USD")
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "desk lamp between 50 and 100",
                "USD",
                previous,
                settingsWithDestination()
        ));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().price().minUsdMinor()).isEqualTo(5_000L);
        assertThat(resolution.plan().price().maxUsdMinor()).isEqualTo(10_000L);
    }

    @Test
    void doesNotReuseAPriceBoundFromAnOlderConversationSearch() {
        UserProductSearchQualificationPlan previous = plan(
                "desk lamp",
                List.of(UserProductSearchQuestionTarget.PRICE),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.MISSING,
                        null,
                        null,
                        UserProductSearchQualificationPlan.Provenance.none()
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(UserProductSearchQuestionTarget.PRICE),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.VALUE,
                        null,
                        10_000L,
                        currentTurn("USD")
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        var query = new GenerateUserProductSearchQualificationQuery(
                "desk lamp",
                "USD",
                previous,
                settingsWithDestination(),
                List.of(),
                List.of(
                        userMessage("running shoes under 100"),
                        assistantMessage("What should the shoes cost?"),
                        userMessage("desk lamp"),
                        assistantMessage("What currency should I use?"),
                        userMessage("USD")
                )
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("PRICE typed bounds do not match the buyer's grounded bound direction and values");
        assertThat(resolution.plan().price().state()).isEqualTo(UserProductSearchFilterState.MISSING);
    }

    @Test
    void ignoresOldConversationPriceBoundsForAnUnrelatedCurrentSearch() {
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        var query = new GenerateUserProductSearchQualificationQuery(
                "desk lamp",
                "desk lamp",
                null,
                settingsWithDestination(),
                List.of(),
                List.of(
                        userMessage("running shoes under 100"),
                        assistantMessage("What currency should I use?"),
                        userMessage("desk lamp")
                )
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().price().state())
                .isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
    }

    @Test
    void shippingOriginPostalCodeDoesNotBecomeAPriceConstraint() {
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(),
                notApplicableCondition(),
                new UserProductSearchQualificationPlan.LocationsFilter(
                        UserProductSearchFilterState.VALUE,
                        List.of(new UserProductSearchQualificationPlan.Location("US", null, "90210")),
                        originalQuery("shipped from US postal code 90210")
                ),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "desk lamp shipped from US postal code 90210",
                "desk lamp shipped from US postal code 90210",
                null,
                settingsWithDestination()
        ));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().price().state())
                .isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
    }

    @Test
    void outageFallbackAsksForCurrencyBeforeUsingAnUnqualifiedPriceBound() {
        UserProductSearchQualificationPlan fallback = resolver.safeFallback(query(
                "desk lamp under 100",
                "desk lamp under 100",
                null,
                settingsWithDestination()
        ));

        assertThat(fallback.price().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(fallback.questionTargets()).containsExactly(UserProductSearchQuestionTarget.PRICE);
        assertThat(fallback.assistantMessage()).contains("currency", "USD");
    }

    @Test
    void outageFallbackAsksForDirectionInsteadOfInventingItForAStandaloneUsdAmount() {
        UserProductSearchQualificationPlan fallback = resolver.safeFallback(query(
                "desk lamp 100 USD",
                "desk lamp 100 USD",
                null,
                settingsWithDestination()
        ));

        assertThat(fallback.price().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(fallback.questionTargets()).containsExactly(UserProductSearchQuestionTarget.PRICE);
        assertThat(fallback.assistantMessage()).contains("minimum", "maximum", "range");
        assertThat(fallback.assistantMessage()).doesNotContain("What currency");
    }

    @Test
    void outageFallbackFailsClosedWhenTheOriginalCannotFitTheSearchQueryContract() {
        String request = "desk lamp " + "with carefully described requirements ".repeat(20);

        assertThat(request.length()).isGreaterThan(500);
        assertThatThrownBy(() -> resolver.safeFallback(query(
                request,
                request,
                null,
                settingsWithDestination()
        )))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("500")
                .hasMessageContaining("restate");
    }

    @Test
    void acceptsTheExactDecimalRatingWithoutTreatingItAsAReviewCount() {
        UserProductSearchQualificationPlan candidate = plan(
                "running shoes",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.VALUE,
                        new java.math.BigDecimal("4.5"),
                        null,
                        originalQuery("4.5")
                ),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "running shoes rated 4.5",
                "running shoes rated 4.5",
                null
        ));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().rating().min()).isEqualByComparingTo("4.5");
    }

    @Test
    void acceptsAnExplicitUppercaseCountryCode() {
        UserProductSearchQualificationPlan candidate = plan(
                "running shoes",
                List.of(),
                notApplicableCondition(),
                new UserProductSearchQualificationPlan.LocationsFilter(
                        UserProductSearchFilterState.VALUE,
                        List.of(new UserProductSearchQualificationPlan.Location("US", null, null)),
                        originalQuery("US")
                ),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "running shoes shipped from US",
                "running shoes shipped from US",
                null
        ));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().shipsFrom().state()).isEqualTo(UserProductSearchFilterState.VALUE);
        assertThat(resolution.plan().shipsFrom().values())
                .extracting(UserProductSearchQualificationPlan.Location::country)
                .containsExactly("US");
    }

    @Test
    void acceptsGenericExplicitAnyWhenThePreviousQuestionTargetedOnlyRating() {
        UserProductSearchQualificationPlan previous = plan(
                "desk lamp",
                List.of(UserProductSearchQuestionTarget.RATING),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                missingRating(),
                notApplicablePriceTier()
        ).withConversation(
                "Chybí mi ještě rating, je ti opravdu jedno?",
                List.of(),
                List.of(UserProductSearchQuestionTarget.RATING)
        );
        UserProductSearchQualificationPlan candidate = plan(
                "desk lamp",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        null,
                        currentTurn("ano")
                ),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "desk lamp", "ano", previous, settingsWithDestination()));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().rating().state()).isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(resolution.plan().rating().provenance().source())
                .isEqualTo(UserProductSearchDecisionSource.CURRENT_USER_TURN);
        assertThat(resolution.plan().missingTargets()).isEmpty();
        assertThat(resolution.plan().questionTargets()).isEmpty();
    }

    @Test
    void acceptsExplicitShippingIndifferenceAfterAskingForTheDestination() {
        UserProductSearchQualificationPlan previous = withShipsTo(
                plan(
                        "black jacket",
                        List.of(UserProductSearchQuestionTarget.SHIPS_TO),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        blackAttributes(),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.MISSING,
                        null,
                        UserProductSearchQualificationPlan.Provenance.none()
                )
        ).withConversation(
                "What country should the order ship to, or does location not matter?",
                List.of(),
                List.of(UserProductSearchQuestionTarget.SHIPS_TO)
        );
        UserProductSearchQualificationPlan candidate = withShipsTo(
                plan(
                        "black jacket",
                        List.of(),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        blackAttributes(),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        currentTurn("I don't care")
                )
        );

        var resolution = resolver.resolve(candidate, query(
                "black jacket", "I don't care", previous));

        assertThat(resolution.valid()).as(resolution.violations().toString()).isTrue();
        assertThat(resolution.plan().shipsTo().state()).isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(resolution.plan().missingTargets()).containsExactly(UserProductSearchQuestionTarget.SIZE);
    }

    @Test
    void acceptsABareSizeAnswerWhenThePreviousQuestionAlsoAskedForDestination() {
        UserProductSearchQualificationPlan previous = pendingRunningShoeSizeAndDestination();
        UserProductSearchQualificationPlan candidate = withShipsTo(
                plan(
                        "running shoes",
                        List.of(UserProductSearchQuestionTarget.SHIPS_TO),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        attributes(
                                notApplicableAttribute(UserProductSearchAttributeName.COLOR),
                                new UserProductSearchQualificationPlan.Attribute(
                                        UserProductSearchAttributeName.SIZE,
                                        UserProductSearchFilterState.VALUE,
                                        List.of("46"),
                                        currentTurn("46")
                                ),
                                notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
                        ),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.MISSING,
                        null,
                        UserProductSearchQualificationPlan.Provenance.none()
                )
        );

        var resolution = resolver.resolve(candidate, query("running shoes", "46", previous));

        assertThat(resolution.valid()).isTrue();
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.SIZE).values())
                .containsExactly("46");
        assertThat(resolution.plan().missingTargets())
                .containsExactly(UserProductSearchQuestionTarget.SHIPS_TO);
    }

    @Test
    void acceptsALowercaseCountryCodeWhenTheCombinedQuestionAskedForDestination() {
        UserProductSearchQualificationPlan previous = pendingRunningShoeSizeAndDestination();
        UserProductSearchQualificationPlan candidate = withShipsTo(
                plan(
                        "running shoes",
                        List.of(UserProductSearchQuestionTarget.SIZE),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        attributes(
                                notApplicableAttribute(UserProductSearchAttributeName.COLOR),
                                missingAttribute(UserProductSearchAttributeName.SIZE),
                                notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
                        ),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.VALUE,
                        new UserProductSearchQualificationPlan.Location("US", null, null),
                        currentTurn("us")
                )
        );

        var resolution = resolver.resolve(candidate, query("running shoes", "us", previous));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().shipsTo().value().country()).isEqualTo("US");
        assertThat(resolution.plan().missingTargets())
                .containsExactly(UserProductSearchQuestionTarget.SIZE);
    }

    @Test
    void acceptsGenericIndifferenceForEveryTargetInTheCombinedQuestion() {
        UserProductSearchQualificationPlan previous = pendingRunningShoeSizeAndDestination();
        UserProductSearchQualificationPlan candidate = withShipsTo(
                plan(
                        "running shoes",
                        List.of(),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        attributes(
                                notApplicableAttribute(UserProductSearchAttributeName.COLOR),
                                new UserProductSearchQualificationPlan.Attribute(
                                        UserProductSearchAttributeName.SIZE,
                                        UserProductSearchFilterState.ANY,
                                        List.of(),
                                        currentTurn("I don't care")
                                ),
                                notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
                        ),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        currentTurn("I don't care")
                )
        );

        var resolution = resolver.resolve(candidate, query(
                "running shoes",
                "I don't care",
                previous
        ));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().shipsTo().state()).isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(resolution.plan().missingTargets()).isEmpty();
    }

    @Test
    void indifferenceCannotEraseAValueFromAnotherClause() {
        UserProductSearchQualificationPlan previous = pendingRunningShoeSizeAndDestination();
        String turn = "size doesn't matter, ship to US";
        UserProductSearchQualificationPlan candidate = withShipsTo(
                plan(
                        "running shoes",
                        List.of(),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        attributes(
                                notApplicableAttribute(UserProductSearchAttributeName.COLOR),
                                new UserProductSearchQualificationPlan.Attribute(
                                        UserProductSearchAttributeName.SIZE,
                                        UserProductSearchFilterState.ANY,
                                        List.of(),
                                        currentTurn(turn)
                                ),
                                notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
                        ),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        currentTurn(turn)
                )
        );

        var resolution = resolver.resolve(candidate, query("running shoes", turn, previous));

        assertThat(resolution.valid()).isFalse();
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(resolution.plan().shipsTo().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(resolution.violations())
                .contains("SHIPS_TO ANY lacks explicit filter-specific indifference");
    }

    @Test
    void destinationIndifferenceCannotEraseAnExplicitSizeValueClause() {
        UserProductSearchQualificationPlan previous = pendingRunningShoeSizeAndDestination();
        String turn = "size XL, destination doesn't matter";
        UserProductSearchQualificationPlan candidate = withShipsTo(
                plan(
                        "running shoes",
                        List.of(),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        attributes(
                                notApplicableAttribute(UserProductSearchAttributeName.COLOR),
                                new UserProductSearchQualificationPlan.Attribute(
                                        UserProductSearchAttributeName.SIZE,
                                        UserProductSearchFilterState.ANY,
                                        List.of(),
                                        currentTurn(turn)
                                ),
                                notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
                        ),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        currentTurn(turn)
                )
        );

        var resolution = resolver.resolve(candidate, query("running shoes", turn, previous));

        assertThat(resolution.valid()).isFalse();
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(resolution.plan().shipsTo().state()).isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(resolution.violations())
                .contains("SIZE ANY lacks explicit filter-specific indifference");
    }

    @Test
    void modelOutageFallbackStillAppliesABarePendingSizeAnswer() {
        UserProductSearchQualificationPlan plan = resolver.safeFallback(query(
                "running shoes",
                "XL",
                pendingRunningShoeSizeAndDestination()
        ));

        assertThat(attribute(plan, UserProductSearchAttributeName.SIZE).values())
                .containsExactly("XL");
        assertThat(plan.missingTargets())
                .containsExactly(UserProductSearchQuestionTarget.SHIPS_TO);
    }

    @Test
    void modelOutageFallbackStillAppliesGenericPendingIndifference() {
        UserProductSearchQualificationPlan plan = resolver.safeFallback(query(
                "running shoes",
                "I don't care",
                pendingRunningShoeSizeAndDestination()
        ));

        assertThat(plan.shipsTo().state()).isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(attribute(plan, UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(plan.missingTargets()).isEmpty();
    }

    @Test
    void modelOutageSanitizesSavedTermsBeforeApplyingGenericPendingIndifference() {
        UserProductSearchQualificationPlan previous = withEffectiveQuery(
                pendingRunningShoeSizeAndDestination(),
                "running shoes 46"
        );
        var query = new GenerateUserProductSearchQualificationQuery(
                "running shoes",
                "I don't care",
                previous,
                settingsWithUsDestination(),
                List.of(new UserProductSearchPreferenceResult(
                        "running-shoes",
                        UserProductSearchAttributeName.SIZE,
                        List.of("46")
                ))
        );

        UserProductSearchQualificationPlan plan = resolver.safeFallback(query);

        assertThat(plan.effectiveQuery()).isEqualTo("running shoes");
        assertThat(plan.shipsTo().state()).isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(attribute(plan, UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(plan.missingTargets()).isEmpty();
    }

    @Test
    void modelOutageRemovesOriginalPossessiveGenderAcrossRemainingSizeTurn() {
        UserProductSearchQualificationPlan previous = plan(
                "men's black jacket",
                List.of(
                        UserProductSearchQuestionTarget.TARGET_GENDER,
                        UserProductSearchQuestionTarget.SIZE
                ),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                attributes(
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.COLOR,
                                UserProductSearchFilterState.VALUE,
                                List.of("Black"),
                                originalQuery("black")
                        ),
                        missingAttribute(UserProductSearchAttributeName.SIZE),
                        missingAttribute(UserProductSearchAttributeName.TARGET_GENDER)
                ),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        UserSettingsResult settings = settingsWithClothingFit("men");

        UserProductSearchQualificationPlan afterGender = resolver.safeFallback(query(
                "men's black jacket",
                "target gender doesn't matter",
                previous,
                settings
        ));
        UserProductSearchQualificationPlan ready = resolver.safeFallback(query(
                "men's black jacket",
                "XL",
                afterGender,
                settings
        ));

        assertThat(afterGender.effectiveQuery()).isEqualTo("black jacket");
        assertThat(afterGender.missingTargets()).containsExactly(UserProductSearchQuestionTarget.SIZE);
        assertThat(ready.effectiveQuery()).isEqualTo("black jacket");
        assertThat(ready.effectiveQuery()).doesNotContain("men", " s ");
        assertThat(attribute(ready, UserProductSearchAttributeName.TARGET_GENDER).state())
                .isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(attribute(ready, UserProductSearchAttributeName.SIZE).values()).containsExactly("XL");
        assertThat(ready.missingTargets()).isEmpty();
    }

    @Test
    void modelOutageFallbackKeepsMixedSizeIndifferenceAndDestinationValueSeparate() {
        UserProductSearchQualificationPlan plan = resolver.safeFallback(query(
                "running shoes",
                "size doesn't matter, ship to US",
                pendingRunningShoeSizeAndDestination()
        ));

        assertThat(attribute(plan, UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(plan.shipsTo().state()).isEqualTo(UserProductSearchFilterState.VALUE);
        assertThat(plan.shipsTo().value().country()).isEqualTo("US");
        assertThat(plan.missingTargets()).isEmpty();
    }

    @Test
    void modelOutageFallbackKeepsMixedSizeValueAndDestinationIndifferenceSeparate() {
        UserProductSearchQualificationPlan plan = resolver.safeFallback(query(
                "running shoes",
                "size XL, destination doesn't matter",
                pendingRunningShoeSizeAndDestination()
        ));

        assertThat(attribute(plan, UserProductSearchAttributeName.SIZE).values())
                .containsExactly("XL");
        assertThat(plan.shipsTo().state()).isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(plan.missingTargets()).isEmpty();
    }

    @Test
    void modelOutageFallbackAppliesLocationIndifferenceOnlyToDestination() {
        UserProductSearchQualificationPlan plan = resolver.safeFallback(query(
                "running shoes",
                "location doesn't matter",
                pendingRunningShoeSizeAndDestination()
        ));

        assertThat(plan.shipsTo().state()).isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(attribute(plan, UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(plan.missingTargets()).containsExactly(UserProductSearchQuestionTarget.SIZE);
    }

    @Test
    void postalCodeAloneRemainsUnresolvedBecauseCountryCannotBeInferred() {
        UserProductSearchQualificationPlan plan = resolver.safeFallback(query(
                "running shoes",
                "94107",
                pendingRunningShoeSizeAndDestination()
        ));

        assertThat(plan.missingTargets()).containsExactlyInAnyOrder(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.SHIPS_TO
        );
        assertThat(plan.assistantMessage())
                .contains("what country should it ship to", "region or postal code");
    }

    @Test
    void modelOutageDoesNotReplaceAnExplicitDestinationWithTheSavedDestination() {
        String request = "running shoes shipped to Canada";
        UserProductSearchQualificationPlan plan = resolver.safeFallback(
                new GenerateUserProductSearchQualificationQuery(
                        request,
                        request,
                        null,
                        settingsWithUsDestination(),
                        List.of()
                )
        );

        assertThat(plan.shipsTo().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(plan.shipsTo().value()).isNull();
        assertThat(plan.missingTargets()).contains(UserProductSearchQuestionTarget.SHIPS_TO);
    }

    @Test
    void modelOutageDoesNotReplaceAnExplicitSizeWithTheDurableSize() {
        String request = "running shoes size 46";
        UserProductSearchQualificationPlan plan = resolver.safeFallback(
                new GenerateUserProductSearchQualificationQuery(
                        request,
                        request,
                        null,
                        settingsWithUsDestination(),
                        List.of(new UserProductSearchPreferenceResult(
                                "running-shoes",
                                UserProductSearchAttributeName.SIZE,
                                List.of("10")
                        ))
                )
        );

        assertThat(attribute(plan, UserProductSearchAttributeName.SIZE)).satisfies(size -> {
            assertThat(size.state()).isEqualTo(UserProductSearchFilterState.MISSING);
            assertThat(size.values()).isEmpty();
        });
        assertThat(plan.missingTargets()).contains(UserProductSearchQuestionTarget.SIZE);
    }

    @Test
    void rejectsEffectiveQueryTermsThatDoNotComeFromTrustedBuyerContext() {
        UserProductSearchQualificationPlan candidate = plan(
                "gaming laptops",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "ebook",
                "ebook",
                null
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .anyMatch(violation -> violation.contains("effectiveQuery"));
        assertThat(resolution.plan().effectiveQuery()).isEqualTo("ebook");
    }

    @Test
    void acceptsEffectiveQuerySoftTermsFromAPriorUserConversationMessage() {
        UserProductSearchQualificationPlan candidate = plan(
                "accessible ebook",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        var query = new GenerateUserProductSearchQualificationQuery(
                "ebook",
                "Find an ebook.",
                null,
                settings(),
                List.of(),
                List.of(
                        userMessage("ebook"),
                        userMessage("Accessible formats matter to me."),
                        userMessage("Find an ebook.")
                )
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).as(resolution.violations().toString()).isTrue();
        assertThat(resolution.plan().effectiveQuery()).isEqualTo("accessible ebook");
    }

    @Test
    void sanitizesSavedSizeRestoredOnlyThroughEffectiveQueryAfterExplicitAny() {
        String turn = "size doesn't matter";
        UserProductSearchQualificationPlan candidate = plan(
                "running shoes 46",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                attributes(
                        notApplicableAttribute(UserProductSearchAttributeName.COLOR),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.SIZE,
                                UserProductSearchFilterState.ANY,
                                List.of(),
                                currentTurn(turn)
                        ),
                        notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
                ),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        var query = new GenerateUserProductSearchQualificationQuery(
                "running shoes",
                turn,
                null,
                settingsWithDestination(),
                List.of(new UserProductSearchPreferenceResult(
                        "running-shoes",
                        UserProductSearchAttributeName.SIZE,
                        List.of("46")
                ))
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.violations()).isEmpty();
        assertThat(resolution.plan().effectiveQuery()).isEqualTo("running shoes");
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.ANY);
    }

    @Test
    void sanitizesSavedTargetGenderRestoredOnlyThroughEffectiveQueryAfterExplicitAny() {
        String turn = "size doesn't matter; target gender doesn't matter";
        UserProductSearchQualificationPlan candidate = plan(
                "men black jacket",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                attributes(
                        blackAttribute(),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.SIZE,
                                UserProductSearchFilterState.ANY,
                                List.of(),
                                currentTurn(turn)
                        ),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.TARGET_GENDER,
                                UserProductSearchFilterState.ANY,
                                List.of(),
                                currentTurn(turn)
                        )
                ),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "black jacket",
                turn,
                null,
                settingsWithClothingFit("men")
        ));

        assertThat(resolution.valid()).as(resolution.violations().toString()).isTrue();
        assertThat(resolution.violations()).isEmpty();
        assertThat(resolution.plan().effectiveQuery()).isEqualTo("black jacket");
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.TARGET_GENDER).state())
                .isEqualTo(UserProductSearchFilterState.ANY);
    }

    @Test
    void removesAnOverriddenShippingConstraintFromTheQueryAndMappedContext() {
        UserProductSearchQualificationPlan.Attribute savedSize =
                new UserProductSearchQualificationPlan.Attribute(
                        UserProductSearchAttributeName.SIZE,
                        UserProductSearchFilterState.VALUE,
                        List.of("46"),
                        new UserProductSearchQualificationPlan.Provenance(
                                UserProductSearchDecisionSource.DURABLE_PREFERENCE,
                                "running-shoes SIZE 46"
                        )
                );
        UserProductSearchQualificationPlan.AttributesFilter attributes = attributes(
                notApplicableAttribute(UserProductSearchAttributeName.COLOR),
                savedSize,
                notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
        );
        UserProductSearchQualificationPlan previous = withShipsTo(
                plan(
                        "running shoes shipping to US",
                        List.of(),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        attributes,
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.VALUE,
                        new UserProductSearchQualificationPlan.Location("US", null, null),
                        originalQuery("shipping to US")
                )
        );
        String turn = "destination doesn't matter";
        UserProductSearchQualificationPlan candidate = withShipsTo(
                withEffectiveQuery(previous, "running shoes shipping to US"),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        currentTurn(turn)
                )
        );
        var query = new GenerateUserProductSearchQualificationQuery(
                "running shoes shipping to US",
                turn,
                previous,
                settingsWithUsDestination(),
                List.of(new UserProductSearchPreferenceResult(
                        "running-shoes",
                        UserProductSearchAttributeName.SIZE,
                        List.of("46")
                ))
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().effectiveQuery()).isEqualTo("running shoes");
        assertThat(resolution.plan().shipsTo().state()).isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(new UserProductSearchQualificationPlanMapper().map(resolution.plan()).shipsTo())
                .isNull();
    }

    @Test
    void stripsTargetSpecificAnyPhrasesFromOriginalQueries() {
        for (IndifferenceQuery scenario : List.of(
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.CONDITION,
                        "desk lamp any condition",
                        "any condition"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.SHIPS_TO,
                        "desk lamp any destination",
                        "any destination"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.SHIPS_FROM,
                        "desk lamp any shipping origin",
                        "any shipping origin"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.PRICE,
                        "desk lamp any budget",
                        "any budget"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.COLOR,
                        "desk lamp any color",
                        "any color"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.SIZE,
                        "desk lamp any size",
                        "any size"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.TARGET_GENDER,
                        "desk lamp any gender",
                        "any gender"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.RATING,
                        "desk lamp any rating",
                        "any rating"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.PRICE_TIER,
                        "desk lamp any price tier",
                        "any price tier"
                )
        )) {
            UserProductSearchQualificationPlan candidate = explicitAnyPlan(
                    scenario.query(),
                    scenario.target(),
                    originalQuery(scenario.evidence())
            );

            var resolution = resolver.resolve(candidate, query(
                    scenario.query(),
                    scenario.query(),
                    null,
                    settingsWithDestination()
            ));

            assertThat(resolution.valid())
                    .as("%s %s", scenario.target(), resolution.violations())
                    .isTrue();
            assertThat(resolution.plan().effectiveQuery())
                    .as(scenario.target().name())
                    .isEqualTo("desk lamp");
        }
    }

    @Test
    void stripsTargetSpecificIndifferencePhrasesAddedOnAFollowUpTurn() {
        UserProductSearchQualificationPlan previous = plan(
                "desk lamp",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        for (IndifferenceQuery scenario : List.of(
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.CONDITION,
                        "desk lamp condition doesn't matter",
                        "condition doesn't matter"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.SHIPS_TO,
                        "desk lamp location doesn't matter",
                        "location doesn't matter"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.SHIPS_FROM,
                        "desk lamp origin doesn't matter",
                        "origin doesn't matter"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.PRICE,
                        "desk lamp budget doesn't matter",
                        "budget doesn't matter"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.COLOR,
                        "desk lamp color doesn't matter",
                        "color doesn't matter"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.SIZE,
                        "desk lamp size doesn't matter",
                        "size doesn't matter"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.TARGET_GENDER,
                        "desk lamp target gender doesn't matter",
                        "target gender doesn't matter"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.RATING,
                        "desk lamp rating doesn't matter",
                        "rating doesn't matter"
                ),
                new IndifferenceQuery(
                        UserProductSearchQuestionTarget.PRICE_TIER,
                        "desk lamp price tier doesn't matter",
                        "price tier doesn't matter"
                )
        )) {
            UserProductSearchQualificationPlan candidate = explicitAnyPlan(
                    scenario.query(),
                    scenario.target(),
                    currentTurn(scenario.evidence())
            );

            var resolution = resolver.resolve(candidate, query(
                    "desk lamp",
                    scenario.evidence(),
                    previous,
                    settingsWithDestination()
            ));

            assertThat(resolution.valid())
                    .as("%s %s", scenario.target(), resolution.violations())
                    .isTrue();
            assertThat(resolution.plan().effectiveQuery())
                    .as(scenario.target().name())
                    .isEqualTo("desk lamp");
        }
    }

    @Test
    void removesGenderedTasteWordingAfterTargetGenderBecomesAny() {
        String turn = "size doesn't matter and target gender doesn't matter";
        UserProductSearchQualificationPlan candidate = plan(
                "women's black jacket",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                attributes(
                        blackAttribute(),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.SIZE,
                                UserProductSearchFilterState.ANY,
                                List.of(),
                                currentTurn(turn)
                        ),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.TARGET_GENDER,
                                UserProductSearchFilterState.ANY,
                                List.of(),
                                currentTurn(turn)
                        )
                ),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        var query = new GenerateUserProductSearchQualificationQuery(
                "black jacket",
                turn,
                null,
                settingsWithDestination(),
                List.of(),
                List.of(),
                tasteProfile("Women's Jackets")
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).as(resolution.violations().toString()).isTrue();
        assertThat(resolution.plan().effectiveQuery()).isEqualTo("black jacket");
    }

    @Test
    void removesSizeTasteWordingAfterSizeBecomesAny() {
        String turn = "size doesn't matter and target gender doesn't matter";
        UserProductSearchQualificationPlan candidate = plan(
                "plus-size black jacket",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                attributes(
                        blackAttribute(),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.SIZE,
                                UserProductSearchFilterState.ANY,
                                List.of(),
                                currentTurn(turn)
                        ),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.TARGET_GENDER,
                                UserProductSearchFilterState.ANY,
                                List.of(),
                                currentTurn(turn)
                        )
                ),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        var query = new GenerateUserProductSearchQualificationQuery(
                "black jacket",
                turn,
                null,
                settingsWithDestination(),
                List.of(),
                List.of(),
                tasteProfile("Plus-size fashion")
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).as(resolution.violations().toString()).isTrue();
        assertThat(resolution.plan().effectiveQuery()).isEqualTo("black jacket");
    }

    @Test
    void historicalUserWordingDoesNotOverrideTheLatestExplicitAny() {
        String turn = "target gender doesn't matter";
        UserProductSearchQualificationPlan candidate = plan(
                "men black jacket",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                attributes(
                        blackAttribute(),
                        notApplicableAttribute(UserProductSearchAttributeName.SIZE),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.TARGET_GENDER,
                                UserProductSearchFilterState.ANY,
                                List.of(),
                                currentTurn(turn)
                        )
                ),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        var query = new GenerateUserProductSearchQualificationQuery(
                "black jacket",
                turn,
                null,
                settingsWithClothingFit("men"),
                List.of(),
                List.of(
                        userMessage("I normally shop for men."),
                        userMessage(turn)
                )
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).as(resolution.violations().toString()).isTrue();
        assertThat(resolution.plan().effectiveQuery()).isEqualTo("black jacket");
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.TARGET_GENDER).state())
                .isEqualTo(UserProductSearchFilterState.ANY);
    }

    @Test
    void keepsSavedLookingTermWhenCurrentUserExplicitlyRequestsIt() {
        String turn = "Keep men in the product query; size doesn't matter; target gender doesn't matter";
        UserProductSearchQualificationPlan candidate = plan(
                "men black jacket",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                attributes(
                        blackAttribute(),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.SIZE,
                                UserProductSearchFilterState.ANY,
                                List.of(),
                                currentTurn(turn)
                        ),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.TARGET_GENDER,
                                UserProductSearchFilterState.ANY,
                                List.of(),
                                currentTurn(turn)
                        )
                ),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "black jacket",
                turn,
                null,
                settingsWithClothingFit("men")
        ));

        assertThat(resolution.valid()).as(resolution.violations().toString()).isTrue();
        assertThat(resolution.plan().effectiveQuery()).isEqualTo("men black jacket");
        assertThat(resolution.violations()).isEmpty();
    }

    @Test
    void doesNotLetAPreviousModelGeneratedEffectiveQueryAuthorizeItself() {
        UserProductSearchQualificationPlan previous = plan(
                "gaming ebook",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        UserProductSearchQualificationPlan candidate = plan(
                "gaming ebook",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "ebook",
                "XL",
                previous
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .anyMatch(violation -> violation.contains("effectiveQuery"));
        assertThat(resolution.plan().effectiveQuery()).isEqualTo("ebook");
    }

    @Test
    void requestWrapperWordsCannotStandInForTheOriginalProductSubject() {
        UserProductSearchQualificationPlan candidate = plan(
                "find laptop",
                List.of(),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        var query = new GenerateUserProductSearchQualificationQuery(
                "find me a jacket",
                "find me a jacket",
                null,
                settings(),
                List.of(),
                List.of(
                        userMessage("I previously considered a laptop."),
                        userMessage("find me a jacket")
                )
        );

        var resolution = resolver.resolve(candidate, query);

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("effectiveQuery must retain a product term from the original request");
        assertThat(resolution.plan().effectiveQuery()).isEqualTo("find me a jacket");
    }

    @Test
    void effectiveQueryMustRetainTheCompoundProductSubject() {
        for (List<String> change : List.of(
                List.of("shoe rack", "shoe cleaner"),
                List.of("phone case", "phone charger"),
                List.of("coffee grinder", "coffee maker")
        )) {
            UserProductSearchQualificationPlan candidate = plan(
                    change.get(1),
                    List.of(),
                    notApplicableCondition(),
                    notApplicableShipsFrom(),
                    notApplicablePrice(),
                    notApplicableAttributes(),
                    notApplicableRating(),
                    notApplicablePriceTier()
            );
            String original = change.get(0);
            String replacement = change.get(1);
            var query = new GenerateUserProductSearchQualificationQuery(
                    original,
                    original,
                    null,
                    settingsWithDestination(),
                    List.of(),
                    List.of(
                            userMessage("I previously considered a " + replacement + "."),
                            userMessage(original)
                    )
            );

            var resolution = resolver.resolve(candidate, query);

            assertThat(resolution.valid()).as("%s -> %s", original, replacement).isFalse();
            assertThat(resolution.violations())
                    .as("%s -> %s", original, replacement)
                    .contains("effectiveQuery must retain a product term from the original request");
            assertThat(resolution.plan().effectiveQuery()).isEqualTo(original);
        }
    }

    @Test
    void resolvesColorAndSizeIndependentlyWithinTheAttributeFilter() {
        UserProductSearchQualificationPlan.AttributesFilter attributes = attributes(
                new UserProductSearchQualificationPlan.Attribute(
                        UserProductSearchAttributeName.COLOR,
                        UserProductSearchFilterState.VALUE,
                        List.of("Blue"),
                        originalQuery("blue")
                ),
                missingAttribute(UserProductSearchAttributeName.SIZE),
                notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
        );
        UserProductSearchQualificationPlan candidate = plan(
                "blue jeans",
                List.of(UserProductSearchQuestionTarget.SIZE),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                notApplicablePrice(),
                attributes,
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "blue jeans", "blue jeans", null, settingsWithDestination()));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().attributes().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.COLOR)).satisfies(color -> {
            assertThat(color.state()).isEqualTo(UserProductSearchFilterState.VALUE);
            assertThat(color.values()).containsExactly("Blue");
        });
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.SIZE)).satisfies(size -> {
            assertThat(size.state()).isEqualTo(UserProductSearchFilterState.MISSING);
            assertThat(size.values()).isEmpty();
        });
        assertThat(resolution.plan().questionTargets()).containsExactly(UserProductSearchQuestionTarget.SIZE);
        assertThat(resolution.plan().missingTargets()).containsExactly(UserProductSearchQuestionTarget.SIZE);
    }

    @Test
    void preservesVerifiedValuesAcrossTurnsWhileApplyingNewCurrentTurnAnswers() {
        UserProductSearchQualificationPlan previous = plan(
                "new blue jeans",
                List.of(UserProductSearchQuestionTarget.PRICE, UserProductSearchQuestionTarget.SIZE),
                new UserProductSearchQualificationPlan.ConditionFilter(
                        UserProductSearchFilterState.VALUE,
                        List.of(UserProductCondition.NEW),
                        originalQuery("new")
                ),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.MISSING,
                        null,
                        null,
                        UserProductSearchQualificationPlan.Provenance.none()
                ),
                attributes(
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.COLOR,
                                UserProductSearchFilterState.VALUE,
                                List.of("Blue"),
                                originalQuery("blue")
                        ),
                        missingAttribute(UserProductSearchAttributeName.SIZE),
                        notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
                ),
                notApplicableRating(),
                notApplicablePriceTier()
        );
        UserProductSearchQualificationPlan candidate = plan(
                "blue jeans",
                List.of(),
                new UserProductSearchQualificationPlan.ConditionFilter(
                        UserProductSearchFilterState.MISSING,
                        List.of(),
                        UserProductSearchQualificationPlan.Provenance.none()
                ),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.VALUE,
                        null,
                        10_000L,
                        currentTurn("under 100 USD")
                ),
                attributes(
                        missingAttribute(UserProductSearchAttributeName.COLOR),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.SIZE,
                                UserProductSearchFilterState.VALUE,
                                List.of("32"),
                                currentTurn("size 32")
                        ),
                        notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
                ),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(
                candidate,
                query("new blue jeans", "size 32, under 100 USD", previous, settingsWithDestination())
        );

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().condition().state()).isEqualTo(UserProductSearchFilterState.VALUE);
        assertThat(resolution.plan().condition().values()).containsExactly(UserProductCondition.NEW);
        assertThat(resolution.plan().condition().provenance()).isEqualTo(previous.condition().provenance());
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.COLOR)).satisfies(color -> {
            assertThat(color.state()).isEqualTo(UserProductSearchFilterState.VALUE);
            assertThat(color.values()).containsExactly("Blue");
            assertThat(color.provenance()).isEqualTo(attribute(previous, UserProductSearchAttributeName.COLOR)
                    .provenance());
        });
        assertThat(attribute(resolution.plan(), UserProductSearchAttributeName.SIZE)).satisfies(size -> {
            assertThat(size.state()).isEqualTo(UserProductSearchFilterState.VALUE);
            assertThat(size.values()).containsExactly("32");
            assertThat(size.provenance().source()).isEqualTo(UserProductSearchDecisionSource.CURRENT_USER_TURN);
        });
        assertThat(resolution.plan().price().state()).isEqualTo(UserProductSearchFilterState.VALUE);
        assertThat(resolution.plan().price().maxUsdMinor()).isEqualTo(10_000L);
        assertThat(resolution.plan().missingTargets()).isEmpty();
        assertThat(resolution.plan().questionTargets()).isEmpty();
    }

    @Test
    void rejectsOriginAndDestinationValuesSwappedAcrossTargetRelations() {
        String request = "football boots shipped from US to CA";
        UserProductSearchQualificationPlan candidate = withShipsTo(
                plan(
                        request,
                        List.of(UserProductSearchQuestionTarget.SHIPS_TO,
                                UserProductSearchQuestionTarget.SHIPS_FROM),
                        notApplicableCondition(),
                        new UserProductSearchQualificationPlan.LocationsFilter(
                                UserProductSearchFilterState.VALUE,
                                List.of(new UserProductSearchQualificationPlan.Location("CA", null, null)),
                                originalQuery(request)
                        ),
                        notApplicablePrice(),
                        notApplicableAttributes(),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.VALUE,
                        new UserProductSearchQualificationPlan.Location("US", null, null),
                        originalQuery(request)
                )
        );

        var resolution = resolver.resolve(candidate, query(request, request, null));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.plan().shipsTo().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(resolution.plan().shipsFrom().state()).isEqualTo(UserProductSearchFilterState.MISSING);
    }

    @Test
    void rejectsPriceAndRatingNumbersTakenFromDifferentRelations() {
        String request = "desk lamp under 100 USD rated 4.5 with 200 reviews";
        UserProductSearchQualificationPlan candidate = plan(
                request,
                List.of(UserProductSearchQuestionTarget.PRICE, UserProductSearchQuestionTarget.RATING),
                notApplicableCondition(),
                notApplicableShipsFrom(),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.VALUE,
                        null,
                        20_000L,
                        originalQuery(request)
                ),
                notApplicableAttributes(),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.VALUE,
                        new java.math.BigDecimal("4.5"),
                        100L,
                        originalQuery(request)
                ),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(request, request, null));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.plan().price().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(resolution.plan().rating().state()).isEqualTo(UserProductSearchFilterState.MISSING);
    }

    private UserProductSearchQualificationPlan pendingRunningShoeSizeAndDestination() {
        return withShipsTo(
                plan(
                        "running shoes",
                        List.of(
                                UserProductSearchQuestionTarget.SIZE,
                                UserProductSearchQuestionTarget.SHIPS_TO
                        ),
                        notApplicableCondition(),
                        notApplicableShipsFrom(),
                        notApplicablePrice(),
                        attributes(
                                notApplicableAttribute(UserProductSearchAttributeName.COLOR),
                                missingAttribute(UserProductSearchAttributeName.SIZE),
                                notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
                        ),
                        notApplicableRating(),
                        notApplicablePriceTier()
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.MISSING,
                        null,
                        UserProductSearchQualificationPlan.Provenance.none()
                )
        ).withConversation(
                "What shoe size do you need, and what country should it ship to? "
                        + "Say “I don’t care” if neither matters.",
                List.of(),
                List.of(
                        UserProductSearchQuestionTarget.SIZE,
                        UserProductSearchQuestionTarget.SHIPS_TO
                )
        );
    }

    private UserProductSearchQualificationPlan plan(
            String effectiveQuery,
            List<UserProductSearchQuestionTarget> questionTargets,
            UserProductSearchQualificationPlan.ConditionFilter condition,
            UserProductSearchQualificationPlan.LocationsFilter shipsFrom,
            UserProductSearchQualificationPlan.PriceFilter price,
            UserProductSearchQualificationPlan.AttributesFilter attributes,
            UserProductSearchQualificationPlan.RatingFilter rating,
            UserProductSearchQualificationPlan.PriceTierFilter priceTier
    ) {
        return new UserProductSearchQualificationPlan(
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION,
                effectiveQuery,
                assistantMessage(questionTargets),
                List.of(),
                questionTargets,
                new UserProductSearchQualificationPlan.AvailableFilter(
                        UserProductSearchFilterState.VALUE,
                        true,
                        UserProductSearchQualificationPlan.Provenance.system("candidate policy")
                ),
                condition,
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE,
                        null,
                        UserProductSearchQualificationPlan.Provenance.none()
                ),
                shipsFrom,
                price,
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE,
                        List.of(),
                        UserProductSearchQualificationPlan.Provenance.none()
                ),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE,
                        List.of(),
                        UserProductSearchQualificationPlan.Provenance.none()
                ),
                attributes,
                rating,
                priceTier,
                List.of()
        );
    }

    private UserProductSearchQualificationPlan withShipsTo(
            UserProductSearchQualificationPlan plan,
            UserProductSearchQualificationPlan.LocationFilter shipsTo
    ) {
        return new UserProductSearchQualificationPlan(
                plan.schemaVersion(),
                plan.effectiveQuery(),
                plan.assistantMessage(),
                plan.suggestedReplies(),
                plan.questionTargets(),
                plan.available(),
                plan.condition(),
                shipsTo,
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

    private UserProductSearchQualificationPlan withEffectiveQuery(
            UserProductSearchQualificationPlan plan,
            String effectiveQuery
    ) {
        return new UserProductSearchQualificationPlan(
                plan.schemaVersion(),
                effectiveQuery,
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

    private UserProductSearchQualificationPlan explicitAnyPlan(
            String effectiveQuery,
            UserProductSearchQuestionTarget target,
            UserProductSearchQualificationPlan.Provenance provenance
    ) {
        UserProductSearchQualificationPlan.Attribute color =
                notApplicableAttribute(UserProductSearchAttributeName.COLOR);
        UserProductSearchQualificationPlan.Attribute size =
                notApplicableAttribute(UserProductSearchAttributeName.SIZE);
        UserProductSearchQualificationPlan.Attribute targetGender =
                notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER);
        if (target == UserProductSearchQuestionTarget.COLOR) {
            color = explicitAnyAttribute(UserProductSearchAttributeName.COLOR, provenance);
        } else if (target == UserProductSearchQuestionTarget.SIZE) {
            size = explicitAnyAttribute(UserProductSearchAttributeName.SIZE, provenance);
        } else if (target == UserProductSearchQuestionTarget.TARGET_GENDER) {
            targetGender = explicitAnyAttribute(UserProductSearchAttributeName.TARGET_GENDER, provenance);
        }

        UserProductSearchQualificationPlan result = plan(
                effectiveQuery,
                List.of(),
                target == UserProductSearchQuestionTarget.CONDITION
                        ? new UserProductSearchQualificationPlan.ConditionFilter(
                                UserProductSearchFilterState.ANY,
                                List.of(),
                                provenance
                        )
                        : notApplicableCondition(),
                target == UserProductSearchQuestionTarget.SHIPS_FROM
                        ? new UserProductSearchQualificationPlan.LocationsFilter(
                                UserProductSearchFilterState.ANY,
                                List.of(),
                                provenance
                        )
                        : notApplicableShipsFrom(),
                target == UserProductSearchQuestionTarget.PRICE
                        ? new UserProductSearchQualificationPlan.PriceFilter(
                                UserProductSearchFilterState.ANY,
                                null,
                                null,
                                provenance
                        )
                        : notApplicablePrice(),
                attributes(color, size, targetGender),
                target == UserProductSearchQuestionTarget.RATING
                        ? new UserProductSearchQualificationPlan.RatingFilter(
                                UserProductSearchFilterState.ANY,
                                null,
                                null,
                                provenance
                        )
                        : notApplicableRating(),
                target == UserProductSearchQuestionTarget.PRICE_TIER
                        ? new UserProductSearchQualificationPlan.PriceTierFilter(
                                UserProductSearchFilterState.ANY,
                                List.of(),
                                provenance
                        )
                        : notApplicablePriceTier()
        );
        if (target != UserProductSearchQuestionTarget.SHIPS_TO) {
            return result;
        }
        return withShipsTo(
                result,
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        provenance
                )
        );
    }

    private UserProductSearchQualificationPlan.Attribute explicitAnyAttribute(
            UserProductSearchAttributeName name,
            UserProductSearchQualificationPlan.Provenance provenance
    ) {
        return new UserProductSearchQualificationPlan.Attribute(
                name,
                UserProductSearchFilterState.ANY,
                List.of(),
                provenance
        );
    }

    private UserProductSearchQualificationPlan.ConditionFilter notApplicableCondition() {
        return new UserProductSearchQualificationPlan.ConditionFilter(
                UserProductSearchFilterState.NOT_APPLICABLE,
                List.of(),
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.LocationsFilter notApplicableShipsFrom() {
        return new UserProductSearchQualificationPlan.LocationsFilter(
                UserProductSearchFilterState.NOT_APPLICABLE,
                List.of(),
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.PriceFilter notApplicablePrice() {
        return new UserProductSearchQualificationPlan.PriceFilter(
                UserProductSearchFilterState.NOT_APPLICABLE,
                null,
                null,
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.AttributesFilter notApplicableAttributes() {
        return attributes(
                notApplicableAttribute(UserProductSearchAttributeName.COLOR),
                notApplicableAttribute(UserProductSearchAttributeName.SIZE),
                notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
        );
    }

    private UserProductSearchQualificationPlan.AttributesFilter blackAttributes() {
        return attributes(
                blackAttribute(),
                notApplicableAttribute(UserProductSearchAttributeName.SIZE),
                notApplicableAttribute(UserProductSearchAttributeName.TARGET_GENDER)
        );
    }

    private UserProductSearchQualificationPlan.Attribute blackAttribute() {
        return new UserProductSearchQualificationPlan.Attribute(
                UserProductSearchAttributeName.COLOR,
                UserProductSearchFilterState.VALUE,
                List.of("Black"),
                originalQuery("black")
        );
    }

    private UserProductSearchQualificationPlan.AttributesFilter attributes(
            UserProductSearchQualificationPlan.Attribute color,
            UserProductSearchQualificationPlan.Attribute size,
            UserProductSearchQualificationPlan.Attribute targetGender
    ) {
        List<UserProductSearchQualificationPlan.Attribute> values = List.of(color, size, targetGender);
        UserProductSearchFilterState state = values.stream()
                .anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.MISSING)
                ? UserProductSearchFilterState.MISSING
                : values.stream().anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.VALUE)
                ? UserProductSearchFilterState.VALUE
                : values.stream().anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.ANY)
                ? UserProductSearchFilterState.ANY
                : UserProductSearchFilterState.NOT_APPLICABLE;
        return new UserProductSearchQualificationPlan.AttributesFilter(state, values);
    }

    private UserProductSearchQualificationPlan.Attribute missingAttribute(UserProductSearchAttributeName name) {
        return new UserProductSearchQualificationPlan.Attribute(
                name,
                UserProductSearchFilterState.MISSING,
                List.of(),
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.Attribute notApplicableAttribute(UserProductSearchAttributeName name) {
        return new UserProductSearchQualificationPlan.Attribute(
                name,
                UserProductSearchFilterState.NOT_APPLICABLE,
                List.of(),
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.RatingFilter missingRating() {
        return new UserProductSearchQualificationPlan.RatingFilter(
                UserProductSearchFilterState.MISSING,
                null,
                null,
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.RatingFilter notApplicableRating() {
        return new UserProductSearchQualificationPlan.RatingFilter(
                UserProductSearchFilterState.NOT_APPLICABLE,
                null,
                null,
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.PriceTierFilter notApplicablePriceTier() {
        return new UserProductSearchQualificationPlan.PriceTierFilter(
                UserProductSearchFilterState.NOT_APPLICABLE,
                List.<UserProductPriceTier>of(),
                UserProductSearchQualificationPlan.Provenance.none()
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

    private UserProductSearchFilterState decisionState(
            UserProductSearchQualificationPlan plan,
            UserProductSearchQuestionTarget target
    ) {
        return switch (target) {
            case CONDITION -> plan.condition().state();
            case SHIPS_TO -> plan.shipsTo().state();
            case SHIPS_FROM -> plan.shipsFrom().state();
            case PRICE -> plan.price().state();
            case COLOR -> attribute(plan, UserProductSearchAttributeName.COLOR).state();
            case SIZE -> attribute(plan, UserProductSearchAttributeName.SIZE).state();
            case TARGET_GENDER -> attribute(plan, UserProductSearchAttributeName.TARGET_GENDER).state();
            case RATING -> plan.rating().state();
            case PRICE_TIER -> plan.priceTier().state();
        };
    }

    private UserProductSearchQualificationPlan.Provenance originalQuery(String evidence) {
        return new UserProductSearchQualificationPlan.Provenance(
                UserProductSearchDecisionSource.ORIGINAL_QUERY,
                evidence
        );
    }

    private UserProductSearchQualificationPlan.Provenance currentTurn(String evidence) {
        return new UserProductSearchQualificationPlan.Provenance(
                UserProductSearchDecisionSource.CURRENT_USER_TURN,
                evidence
        );
    }

    private UserProductSearchQualificationPlan.Provenance conversation(String evidence) {
        return new UserProductSearchQualificationPlan.Provenance(
                UserProductSearchDecisionSource.CONVERSATION,
                evidence
        );
    }

    private UserProductSearchConversationMessage userMessage(String text) {
        return new UserProductSearchConversationMessage(
                UserProductSearchConversationMessage.Role.USER,
                text
        );
    }

    private UserProductSearchConversationMessage assistantMessage(String text) {
        return new UserProductSearchConversationMessage(
                UserProductSearchConversationMessage.Role.ASSISTANT,
                text
        );
    }

    private UserTasteProfileResult tasteProfile(String label) {
        Instant now = Instant.parse("2026-07-17T10:00:00Z");
        return new UserTasteProfileResult(
                "profile",
                List.of(new UserTasteSignalResult(
                        UUID.randomUUID(),
                        UserTasteSignalType.CATEGORY,
                        label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"),
                        label,
                        1.0d,
                        1,
                        0,
                        "SAVE",
                        null,
                        UserTasteSuggestionStatus.PENDING,
                        UserTasteSignalStatus.ACTIVE,
                        now,
                        now
                )),
                List.of()
        );
    }

    private String assistantMessage(List<UserProductSearchQuestionTarget> questionTargets) {
        if (questionTargets.isEmpty()) {
            return "Ready to search.";
        }
        return "Please provide " + questionTargets.stream()
                .map(this::questionLabel)
                .reduce((left, right) -> left + " " + right)
                .orElseThrow() + " preferences.";
    }

    private String questionLabel(UserProductSearchQuestionTarget target) {
        return switch (target) {
            case CONDITION -> "condition";
            case SHIPS_TO -> "delivery";
            case SHIPS_FROM -> "shipping origin";
            case PRICE -> "budget";
            case COLOR -> "color";
            case SIZE -> "size";
            case TARGET_GENDER -> "target gender";
            case RATING -> "rating";
            case PRICE_TIER -> "price tier";
        };
    }

    private GenerateUserProductSearchQualificationQuery query(
            String originalQuery,
            String message,
            UserProductSearchQualificationPlan previous
    ) {
        return query(originalQuery, message, previous, settings());
    }

    private GenerateUserProductSearchQualificationQuery query(
            String originalQuery,
            String message,
            UserProductSearchQualificationPlan previous,
            UserSettingsResult settings
    ) {
        return new GenerateUserProductSearchQualificationQuery(
                originalQuery,
                message,
                previous,
                settings,
                List.of()
        );
    }

    private UserSettingsResult settingsWithDestination() {
        Instant now = Instant.parse("2026-07-17T10:00:00Z");
        UserLocationResult location = new UserLocationResult(
                "home", "Czech Republic", "CZ", "Prague", "18600", "Prague", "Prague");
        return new UserSettingsResult(
                null,
                null,
                location,
                List.of(location),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                now,
                now
        );
    }

    private UserSettingsResult settingsWithUsDestination() {
        Instant now = Instant.parse("2026-07-17T10:00:00Z");
        UserLocationResult location = new UserLocationResult(
                "home", "United States", "US", "CA", "94107", "California", "San Francisco");
        return new UserSettingsResult(
                null,
                null,
                location,
                List.of(location),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                now,
                now
        );
    }

    private UserSettingsResult settingsWithClothingFit(String clothingFit) {
        UserSettingsResult base = settingsWithDestination();
        return new UserSettingsResult(
                base.budget(),
                clothingFit,
                base.location(),
                base.locations(),
                base.filters(),
                base.availableFilters(),
                base.parsedFilterIds(),
                base.unmappedPreferences(),
                base.createdAt(),
                base.updatedAt()
        );
    }

    private UserSettingsResult settings() {
        Instant now = Instant.parse("2026-07-17T10:00:00Z");
        return new UserSettingsResult(
                null,
                null,
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

    private record IndifferenceQuery(
            UserProductSearchQuestionTarget target,
            String query,
            String evidence
    ) {
    }
}
