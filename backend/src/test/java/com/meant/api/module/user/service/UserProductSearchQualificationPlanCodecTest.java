package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.constant.UserProductCondition;
import com.meant.api.module.user.constant.UserProductPriceTier;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UserProductSearchQualificationPlanCodecTest {

    @Test
    void roundTripsConcreteTypedPlanWithoutGenericJsonBags() {
        UserProductSearchQualificationPlanCodec codec = new UserProductSearchQualificationPlanCodec(
                new ObjectMapper());
        UserProductSearchQualificationPlan plan = new UserProductSearchQualificationPlan(
                "black trail running shoes",
                "Ready to search.",
                List.of(),
                new UserProductSearchQualificationPlan.AvailableFilter(
                        UserProductSearchFilterState.VALUE, true),
                new UserProductSearchQualificationPlan.ConditionFilter(
                        UserProductSearchFilterState.VALUE, List.of(UserProductCondition.NEW)),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.VALUE,
                        new UserProductSearchQualificationPlan.Location("US", "CA", "90210")),
                new UserProductSearchQualificationPlan.LocationsFilter(
                        UserProductSearchFilterState.ANY, List.of()),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.VALUE, null, 15_000L),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.AttributesFilter(
                        UserProductSearchFilterState.VALUE,
                        List.of(new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.SIZE, List.of("10", "10.5")))),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.VALUE, new BigDecimal("4.5"), 10L),
                new UserProductSearchQualificationPlan.PriceTierFilter(
                        UserProductSearchFilterState.VALUE,
                        List.of(UserProductPriceTier.LOW, UserProductPriceTier.MEDIUM))
        );

        String encoded = codec.encode(plan);

        assertThat(codec.decode(encoded)).isEqualTo(plan);
        assertThat(encoded).contains("\"SIZE\"").doesNotContain("javaType");
    }
}
