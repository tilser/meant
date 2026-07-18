package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.service.dto.ProductSellingPlanGroup;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Converts the typed catalog wire model before it enters module service DTOs. */
@Component
public class ProductSellingPlanGroupMapper {

    List<ProductSellingPlanGroup> map(List<ProductDetailsResponse.SellingPlanGroup> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(this::group)
                .filter(Objects::nonNull)
                .toList();
    }

    private ProductSellingPlanGroup group(ProductDetailsResponse.SellingPlanGroup value) {
        return new ProductSellingPlanGroup(
                value.id(),
                value.name(),
                value.appName(),
                value.options().stream()
                        .filter(Objects::nonNull)
                        .map(option -> new ProductSellingPlanGroup.GroupOption(option.name(), option.values()))
                        .toList(),
                value.sellingPlans().stream()
                        .filter(Objects::nonNull)
                        .map(plan -> new ProductSellingPlanGroup.SellingPlan(
                                plan.id(), plan.name(), plan.description(), plan.options().stream()
                                        .filter(Objects::nonNull)
                                        .map(option -> new ProductSellingPlanGroup.Option(
                                                option.name(), option.value()))
                                        .toList()))
                        .toList());
    }
}
