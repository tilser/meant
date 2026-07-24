package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeName;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryCondition;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryPriceTier;
import com.meant.api.module.user.constant.UserProductCondition;
import com.meant.api.module.user.constant.UserProductPriceTier;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserProductSearchQualificationPlanMapperTest {

    private final UserProductSearchQualificationPlanMapper mapper =
            new UserProductSearchQualificationPlanMapper();

    @Test
    void mapsEverySupportedReadyFilterWithoutCategorySpecificBranches() {
        UserProductSearchQualificationPlan plan = new UserProductSearchQualificationPlan(
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION,
                "trail running shoes",
                "I have everything I need.",
                List.of(),
                List.of(),
                new UserProductSearchQualificationPlan.AvailableFilter(value(), true),
                new UserProductSearchQualificationPlan.ConditionFilter(
                        value(), List.of(UserProductCondition.NEW)),
                new UserProductSearchQualificationPlan.LocationFilter(
                        value(), new UserProductSearchQualificationPlan.Location("US", "CA", "90210")),
                new UserProductSearchQualificationPlan.LocationsFilter(
                        value(), List.of(new UserProductSearchQualificationPlan.Location("CA", null, null))),
                new UserProductSearchQualificationPlan.PriceFilter(value(), 5000L, 15000L),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        value(), List.of("gid://shopify/Shop/123")),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        value(), List.of("gid://shopify/TaxonomyCategory/aa-8-1")),
                new UserProductSearchQualificationPlan.AttributesFilter(
                        value(),
                        List.of(
                                new UserProductSearchQualificationPlan.Attribute(
                                        UserProductSearchAttributeName.COLOR, List.of("Black")),
                                new UserProductSearchQualificationPlan.Attribute(
                                        UserProductSearchAttributeName.SIZE, List.of("10", "10.5")),
                                new UserProductSearchQualificationPlan.Attribute(
                                        UserProductSearchAttributeName.TARGET_GENDER, List.of("Men"))
                        )),
                new UserProductSearchQualificationPlan.RatingFilter(
                        value(), new BigDecimal("4.5"), 10L),
                new UserProductSearchQualificationPlan.PriceTierFilter(
                        value(), List.of(UserProductPriceTier.LOW, UserProductPriceTier.MEDIUM)),
                List.of()
        );

        var filters = mapper.map(plan);

        assertThat(filters.available()).isTrue();
        assertThat(filters.conditions()).containsExactly(CatalogDiscoveryCondition.NEW);
        assertThat(filters.shipsTo())
                .satisfies(location -> {
                    assertThat(location.country()).isEqualTo("US");
                    assertThat(location.region()).isEqualTo("CA");
                    assertThat(location.postalCode()).isEqualTo("90210");
                });
        assertThat(filters.shipsFrom()).extracting(location -> location.country()).containsExactly("CA");
        assertThat(filters.price().min()).isEqualTo(5000L);
        assertThat(filters.price().max()).isEqualTo(15000L);
        assertThat(filters.shopIds()).containsExactly("gid://shopify/Shop/123");
        assertThat(filters.categoryIds()).containsExactly("gid://shopify/TaxonomyCategory/aa-8-1");
        assertThat(filters.attributes()).extracting(attribute -> attribute.name())
                .containsExactly(
                        CatalogDiscoveryAttributeName.COLOR,
                        CatalogDiscoveryAttributeName.SIZE,
                        CatalogDiscoveryAttributeName.TARGET_GENDER);
        assertThat(filters.rating().variantMinimum()).isEqualByComparingTo("4.5");
        assertThat(filters.rating().variantMinimumCount()).isEqualTo(10L);
        assertThat(filters.priceTiers())
                .containsExactly(CatalogDiscoveryPriceTier.LOW, CatalogDiscoveryPriceTier.MEDIUM);
    }

    @Test
    void refusesToExecuteAPlanThatStillNeedsInput() {
        UserProductSearchQualificationPlan plan = new UserProductSearchQualificationPlan(
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION,
                "t-shirt",
                "Which size do you need?",
                List.of("M", "L"),
                List.of(),
                new UserProductSearchQualificationPlan.AvailableFilter(value(), true),
                new UserProductSearchQualificationPlan.ConditionFilter(any(), List.of()),
                new UserProductSearchQualificationPlan.LocationFilter(any(), null),
                new UserProductSearchQualificationPlan.LocationsFilter(any(), List.of()),
                new UserProductSearchQualificationPlan.PriceFilter(any(), null, null),
                new UserProductSearchQualificationPlan.ReferenceFilter(any(), List.of()),
                new UserProductSearchQualificationPlan.ReferenceFilter(any(), List.of()),
                new UserProductSearchQualificationPlan.AttributesFilter(
                        UserProductSearchFilterState.MISSING,
                        List.of(new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.SIZE,
                                UserProductSearchFilterState.MISSING,
                                List.of(),
                                UserProductSearchQualificationPlan.Provenance.none()
                        ))),
                new UserProductSearchQualificationPlan.RatingFilter(any(), null, null),
                new UserProductSearchQualificationPlan.PriceTierFilter(any(), List.of()),
                List.of()
        );

        assertThatThrownBy(() -> mapper.map(plan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("complete");
    }

    @Test
    void mapsOnlyAttributesWithIndividualValueDecisions() {
        UserProductSearchQualificationPlan plan = completePlan(new UserProductSearchQualificationPlan.AttributesFilter(
                value(),
                List.of(
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.COLOR,
                                value(),
                                List.of("Blue"),
                                UserProductSearchQualificationPlan.Provenance.none()
                        ),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.SIZE,
                                any(),
                                List.of(),
                                UserProductSearchQualificationPlan.Provenance.none()
                        ),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.TARGET_GENDER,
                                UserProductSearchFilterState.NOT_APPLICABLE,
                                List.of(),
                                UserProductSearchQualificationPlan.Provenance.none()
                        )
                )
        ));

        var filters = mapper.map(plan);

        assertThat(filters.attributes()).singleElement().satisfies(attribute -> {
            assertThat(attribute.name()).isEqualTo(CatalogDiscoveryAttributeName.COLOR);
            assertThat(attribute.values()).containsExactly("Blue");
        });
    }

    @Test
    void refusesAnInconsistentPlanWithAnIndividuallyMissingAttribute() {
        UserProductSearchQualificationPlan plan = completePlan(new UserProductSearchQualificationPlan.AttributesFilter(
                value(),
                List.of(
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.COLOR,
                                value(),
                                List.of("Blue"),
                                UserProductSearchQualificationPlan.Provenance.none()
                        ),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.SIZE,
                                UserProductSearchFilterState.MISSING,
                                List.of(),
                                UserProductSearchQualificationPlan.Provenance.none()
                        ),
                        new UserProductSearchQualificationPlan.Attribute(
                                UserProductSearchAttributeName.TARGET_GENDER,
                                UserProductSearchFilterState.NOT_APPLICABLE,
                                List.of(),
                                UserProductSearchQualificationPlan.Provenance.none()
                        )
                )
        ));

        assertThatThrownBy(() -> mapper.map(plan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("complete");
    }

    private UserProductSearchQualificationPlan completePlan(
            UserProductSearchQualificationPlan.AttributesFilter attributes
    ) {
        return new UserProductSearchQualificationPlan(
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION,
                "blue jeans",
                "Ready to search.",
                List.of(),
                List.of(),
                new UserProductSearchQualificationPlan.AvailableFilter(value(), true),
                new UserProductSearchQualificationPlan.ConditionFilter(any(), List.of()),
                new UserProductSearchQualificationPlan.LocationFilter(any(), null),
                new UserProductSearchQualificationPlan.LocationsFilter(any(), List.of()),
                new UserProductSearchQualificationPlan.PriceFilter(any(), null, null),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                attributes,
                new UserProductSearchQualificationPlan.RatingFilter(any(), null, null),
                new UserProductSearchQualificationPlan.PriceTierFilter(any(), List.of()),
                List.of()
        );
    }

    private UserProductSearchFilterState value() {
        return UserProductSearchFilterState.VALUE;
    }

    private UserProductSearchFilterState any() {
        return UserProductSearchFilterState.ANY;
    }
}
