package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.constant.UserProductCondition;
import com.meant.api.module.user.constant.UserProductPriceTier;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
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
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION,
                "black trail running shoes",
                "Ready to search.",
                List.of(),
                List.of(),
                new UserProductSearchQualificationPlan.AvailableFilter(
                        UserProductSearchFilterState.VALUE,
                        true,
                        UserProductSearchQualificationPlan.Provenance.system("sale-ready products only")
                ),
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
                                UserProductSearchAttributeName.SIZE,
                                UserProductSearchFilterState.VALUE,
                                List.of("10", "10.5"),
                                new UserProductSearchQualificationPlan.Provenance(
                                        UserProductSearchDecisionSource.CURRENT_USER_TURN,
                                        "10 or 10.5"
                                )
                        ))),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.VALUE, new BigDecimal("4.5"), 10L),
                new UserProductSearchQualificationPlan.PriceTierFilter(
                        UserProductSearchFilterState.VALUE,
                        List.of(UserProductPriceTier.LOW, UserProductPriceTier.MEDIUM)),
                List.of()
        );

        String encoded = codec.encode(plan);

        UserProductSearchQualificationPlan decoded = codec.decode(encoded);

        assertThat(decoded).isEqualTo(plan);
        assertThat(decoded.currentSchema()).isTrue();
        assertThat(decoded.attributes().values()).singleElement().satisfies(attribute -> {
            assertThat(attribute.state()).isEqualTo(UserProductSearchFilterState.VALUE);
            assertThat(attribute.provenance().source())
                    .isEqualTo(UserProductSearchDecisionSource.CURRENT_USER_TURN);
        });
        assertThat(encoded)
                .contains("\"schemaVersion\":2", "\"SIZE\"", "\"CURRENT_USER_TURN\"")
                .doesNotContain("javaType");
    }

    @Test
    void decodesAPlanWithoutSchemaVersionAsLegacy() {
        UserProductSearchQualificationPlanCodec codec = new UserProductSearchQualificationPlanCodec(
                new ObjectMapper());
        String legacyJson = """
                {
                  "effectiveQuery": "blue jeans",
                  "assistantMessage": "Ready to search.",
                  "suggestedReplies": [],
                  "available": {"state": "VALUE", "value": true},
                  "condition": {"state": "ANY", "values": []},
                  "shipsTo": {"state": "ANY", "value": null},
                  "shipsFrom": {"state": "ANY", "values": []},
                  "price": {"state": "ANY", "minUsdMinor": null, "maxUsdMinor": null},
                  "shops": {"state": "NOT_APPLICABLE", "values": []},
                  "categories": {"state": "NOT_APPLICABLE", "values": []},
                  "attributes": {
                    "state": "VALUE",
                    "values": [{"name": "COLOR", "values": ["Blue"]}]
                  },
                  "rating": {"state": "ANY", "min": null, "minCount": null},
                  "priceTier": {"state": "ANY", "values": []},
                  "durableAttributes": []
                }
                """;

        UserProductSearchQualificationPlan decoded = codec.decode(legacyJson);

        assertThat(decoded.schemaVersion()).isEqualTo(1);
        assertThat(decoded.currentSchema()).isFalse();
        assertThat(decoded.attributes().values()).singleElement().satisfies(attribute -> {
            assertThat(attribute.state()).isEqualTo(UserProductSearchFilterState.VALUE);
            assertThat(attribute.provenance().source()).isEqualTo(UserProductSearchDecisionSource.NONE);
        });
    }
}
