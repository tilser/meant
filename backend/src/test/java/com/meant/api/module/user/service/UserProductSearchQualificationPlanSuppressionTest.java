package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.constant.UserProductCondition;
import com.meant.api.module.user.constant.UserProductPriceTier;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserProductSearchQualificationPlanSuppressionTest {

    @Test
    void explicitAnyIncludesOnlyBuyerGroundedAnyDecisionsWithEvidence() {
        var buyer = provenance(UserProductSearchDecisionSource.CURRENT_USER_TURN, "I do not care");
        var original = provenance(UserProductSearchDecisionSource.ORIGINAL_QUERY, "any color");
        var conversation = provenance(UserProductSearchDecisionSource.CONVERSATION, "whatever size");
        UserProductSearchQualificationPlan plan = plan(
                new UserProductSearchQualificationPlan.ConditionFilter(
                        UserProductSearchFilterState.ANY,
                        List.of(),
                        UserProductSearchQualificationPlan.Provenance.system("not applicable")
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        buyer
                ),
                new UserProductSearchQualificationPlan.LocationsFilter(
                        UserProductSearchFilterState.ANY,
                        List.of(),
                        provenance(UserProductSearchDecisionSource.PROFILE, "saved origin")
                ),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        null,
                        provenance(UserProductSearchDecisionSource.DURABLE_PREFERENCE, "saved price")
                ),
                List.of(
                        attribute(
                                UserProductSearchAttributeName.COLOR,
                                UserProductSearchFilterState.ANY,
                                original
                        ),
                        attribute(
                                UserProductSearchAttributeName.SIZE,
                                UserProductSearchFilterState.ANY,
                                conversation
                        ),
                        attribute(
                                UserProductSearchAttributeName.TARGET_GENDER,
                                UserProductSearchFilterState.ANY,
                                UserProductSearchQualificationPlan.Provenance.system("irrelevant")
                        )
                ),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        null,
                        UserProductSearchQualificationPlan.Provenance.none()
                ),
                new UserProductSearchQualificationPlan.PriceTierFilter(
                        UserProductSearchFilterState.ANY,
                        List.of(),
                        provenance(UserProductSearchDecisionSource.CURRENT_USER_TURN, " ")
                )
        );

        assertThat(plan.explicitAnyTargets()).containsExactlyInAnyOrder(
                UserProductSearchQuestionTarget.SHIPS_TO,
                UserProductSearchQuestionTarget.COLOR,
                UserProductSearchQuestionTarget.SIZE
        );
    }

    @Test
    void profileSuppressionAlsoIncludesBuyerGroundedValuesButNotProfileValues() {
        UserProductSearchQualificationPlan plan = plan(
                new UserProductSearchQualificationPlan.ConditionFilter(
                        UserProductSearchFilterState.VALUE,
                        List.of(UserProductCondition.NEW),
                        provenance(UserProductSearchDecisionSource.PROFILE, "new only")
                ),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        provenance(UserProductSearchDecisionSource.CURRENT_USER_TURN, "any destination")
                ),
                new UserProductSearchQualificationPlan.LocationsFilter(
                        UserProductSearchFilterState.ANY,
                        List.of(),
                        UserProductSearchQualificationPlan.Provenance.system("not applicable")
                ),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        null,
                        UserProductSearchQualificationPlan.Provenance.system("not applicable")
                ),
                List.of(
                        attribute(
                                UserProductSearchAttributeName.SIZE,
                                UserProductSearchFilterState.VALUE,
                                provenance(UserProductSearchDecisionSource.DURABLE_PREFERENCE, "46")
                        ),
                        attribute(
                                UserProductSearchAttributeName.TARGET_GENDER,
                                UserProductSearchFilterState.VALUE,
                                provenance(UserProductSearchDecisionSource.CURRENT_USER_TURN, "women")
                        )
                ),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.ANY,
                        null,
                        null,
                        UserProductSearchQualificationPlan.Provenance.system("not applicable")
                ),
                new UserProductSearchQualificationPlan.PriceTierFilter(
                        UserProductSearchFilterState.ANY,
                        List.of(UserProductPriceTier.MEDIUM),
                        UserProductSearchQualificationPlan.Provenance.system("not applicable")
                )
        );

        assertThat(plan.explicitAnyTargets())
                .containsExactly(UserProductSearchQuestionTarget.SHIPS_TO);
        assertThat(plan.profileSuppressionTargets()).containsExactlyInAnyOrder(
                UserProductSearchQuestionTarget.SHIPS_TO,
                UserProductSearchQuestionTarget.TARGET_GENDER
        );
    }

    private UserProductSearchQualificationPlan plan(
            UserProductSearchQualificationPlan.ConditionFilter condition,
            UserProductSearchQualificationPlan.LocationFilter shipsTo,
            UserProductSearchQualificationPlan.LocationsFilter shipsFrom,
            UserProductSearchQualificationPlan.PriceFilter price,
            List<UserProductSearchQualificationPlan.Attribute> attributes,
            UserProductSearchQualificationPlan.RatingFilter rating,
            UserProductSearchQualificationPlan.PriceTierFilter priceTier
    ) {
        return new UserProductSearchQualificationPlan(
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION,
                "running shoes",
                "Ready to search.",
                List.of(),
                List.of(),
                new UserProductSearchQualificationPlan.AvailableFilter(
                        UserProductSearchFilterState.VALUE,
                        true,
                        UserProductSearchQualificationPlan.Provenance.system("available only")
                ),
                condition,
                shipsTo,
                shipsFrom,
                price,
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE,
                        List.of()
                ),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE,
                        List.of()
                ),
                new UserProductSearchQualificationPlan.AttributesFilter(
                        UserProductSearchFilterState.VALUE,
                        attributes
                ),
                rating,
                priceTier,
                List.of()
        );
    }

    private UserProductSearchQualificationPlan.Attribute attribute(
            UserProductSearchAttributeName name,
            UserProductSearchFilterState state,
            UserProductSearchQualificationPlan.Provenance provenance
    ) {
        return new UserProductSearchQualificationPlan.Attribute(name, state, List.of("value"), provenance);
    }

    private UserProductSearchQualificationPlan.Provenance provenance(
            UserProductSearchDecisionSource source,
            String evidence
    ) {
        return new UserProductSearchQualificationPlan.Provenance(source, evidence);
    }
}
