package com.meant.api.module.user.service;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeName;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryCondition;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryLocation;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryPrice;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryPriceTier;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRating;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts a validated qualification plan to provider-neutral discovery constraints. */
@Component
public class UserProductSearchQualificationPlanMapper {

    public CatalogDiscoveryFilters map(UserProductSearchQualificationPlan plan) {
        if (plan == null
                || !plan.currentSchema()
                || !plan.missingFilters().isEmpty()
                || !plan.missingTargets().isEmpty()) {
            throw new IllegalArgumentException("A complete product-search qualification plan is required");
        }
        return mapValues(plan);
    }

    /** Maps every currently known decision while leaving missing dimensions unconstrained. */
    public CatalogDiscoveryFilters mapAvailable(UserProductSearchQualificationPlan plan) {
        if (plan == null || !plan.currentSchema()) {
            throw new IllegalArgumentException("A current product-search qualification plan is required");
        }
        return mapValues(plan);
    }

    private CatalogDiscoveryFilters mapValues(UserProductSearchQualificationPlan plan) {
        return new CatalogDiscoveryFilters(
                available(plan.available()),
                conditions(plan.condition()),
                shipsTo(plan.shipsTo()),
                shipsFrom(plan.shipsFrom()),
                price(plan.price()),
                values(plan.shops().state(), plan.shops().values()),
                values(plan.categories().state(), plan.categories().values()),
                attributes(plan.attributes()),
                rating(plan.rating()),
                priceTiers(plan.priceTier())
        );
    }

    private Boolean available(UserProductSearchQualificationPlan.AvailableFilter filter) {
        if (!hasValue(filter.state())) {
            return null;
        }
        if (filter.value() == null) {
            throw new IllegalArgumentException("Qualified availability value is required");
        }
        return filter.value();
    }

    private List<CatalogDiscoveryCondition> conditions(
            UserProductSearchQualificationPlan.ConditionFilter filter
    ) {
        return values(filter.state(), filter.values()).stream()
                .map(value -> CatalogDiscoveryCondition.valueOf(value.name()))
                .toList();
    }

    private CatalogDiscoveryLocation shipsTo(UserProductSearchQualificationPlan.LocationFilter filter) {
        if (!hasValue(filter.state())) {
            return null;
        }
        if (filter.value() == null) {
            throw new IllegalArgumentException("Qualified ships-to value is required");
        }
        return location(filter.value());
    }

    private List<CatalogDiscoveryLocation> shipsFrom(
            UserProductSearchQualificationPlan.LocationsFilter filter
    ) {
        return values(filter.state(), filter.values()).stream().map(this::location).toList();
    }

    private CatalogDiscoveryPrice price(UserProductSearchQualificationPlan.PriceFilter filter) {
        if (!hasValue(filter.state())) {
            return null;
        }
        return new CatalogDiscoveryPrice(filter.minUsdMinor(), filter.maxUsdMinor());
    }

    private List<CatalogDiscoveryAttributeFilter> attributes(
            UserProductSearchQualificationPlan.AttributesFilter filter
    ) {
        return filter.values().stream()
                .filter(attribute -> hasValue(attribute.state()))
                .map(attribute -> new CatalogDiscoveryAttributeFilter(
                        CatalogDiscoveryAttributeName.valueOf(attribute.name().name()),
                        values(attribute.state(), attribute.values())))
                .toList();
    }

    private CatalogDiscoveryRating rating(UserProductSearchQualificationPlan.RatingFilter filter) {
        if (!hasValue(filter.state())) {
            return null;
        }
        return new CatalogDiscoveryRating(filter.min(), filter.minCount());
    }

    private List<CatalogDiscoveryPriceTier> priceTiers(
            UserProductSearchQualificationPlan.PriceTierFilter filter
    ) {
        return values(filter.state(), filter.values()).stream()
                .map(value -> CatalogDiscoveryPriceTier.valueOf(value.name()))
                .toList();
    }

    private CatalogDiscoveryLocation location(UserProductSearchQualificationPlan.Location value) {
        return new CatalogDiscoveryLocation(value.country(), value.region(), value.postalCode());
    }

    private <T> List<T> values(UserProductSearchFilterState state, List<T> values) {
        if (!hasValue(state)) {
            return List.of();
        }
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Qualified filter values are required");
        }
        return values;
    }

    private boolean hasValue(UserProductSearchFilterState state) {
        return state == UserProductSearchFilterState.VALUE;
    }
}
