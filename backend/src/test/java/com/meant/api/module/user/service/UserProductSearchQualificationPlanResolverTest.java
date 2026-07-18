package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.constant.UserProductCondition;
import com.meant.api.module.user.constant.UserProductPriceTier;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserProductSearchQualificationPlanResolverTest {

    private final UserProductSearchQualificationPlanResolver resolver =
            new UserProductSearchQualificationPlanResolver();

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

        var resolution = resolver.resolve(candidate, query("desk lamp", "desk lamp", null));

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
                settings(),
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
                settings(),
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
                        originalQuery("$15.00")
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "running shoes for $15.00",
                "running shoes for $15.00",
                null
        ));

        assertThat(resolution.valid()).isFalse();
        assertThat(resolution.violations())
                .contains("PRICE provenance evidence does not support its typed value");
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
                        originalQuery("$15.00")
                ),
                notApplicableAttributes(),
                notApplicableRating(),
                notApplicablePriceTier()
        );

        var resolution = resolver.resolve(candidate, query(
                "running shoes for $15.00",
                "running shoes for $15.00",
                null
        ));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().price().maxUsdMinor()).isEqualTo(1_500L);
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

        var resolution = resolver.resolve(candidate, query("desk lamp", "ano", previous));

        assertThat(resolution.valid()).isTrue();
        assertThat(resolution.plan().rating().state()).isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(resolution.plan().rating().provenance().source())
                .isEqualTo(UserProductSearchDecisionSource.CURRENT_USER_TURN);
        assertThat(resolution.plan().missingTargets()).isEmpty();
        assertThat(resolution.plan().questionTargets()).isEmpty();
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

        var resolution = resolver.resolve(candidate, query("blue jeans", "blue jeans", null));

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
                        currentTurn("under 100")
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
                query("new blue jeans", "size 32, under 100", previous)
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
        return new GenerateUserProductSearchQualificationQuery(
                originalQuery,
                message,
                previous,
                settings(),
                List.of()
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
}
