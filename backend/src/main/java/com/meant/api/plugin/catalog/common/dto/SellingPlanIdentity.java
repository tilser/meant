package com.meant.api.plugin.catalog.common.dto;

import java.util.Comparator;
import java.util.List;

public record SellingPlanIdentity(
        ExternalIdentifier groupReference,
        ExternalIdentifier planReference,
        List<SellingPlanOption> options
) {

    public SellingPlanIdentity {
        if (groupReference == null && planReference == null) {
            throw new IllegalArgumentException("Selling-plan identity needs a group or plan reference");
        }
        if ((groupReference != null && groupReference.type() != ExternalIdentifierType.SELLING_PLAN_GROUP)
                || (planReference != null && planReference.type() != ExternalIdentifierType.SELLING_PLAN)) {
            throw new IllegalArgumentException("Selling-plan references must use their expected identifier types");
        }
        options = options == null
                ? List.of()
                : options.stream()
                        .sorted(Comparator.comparing(SellingPlanOption::name).thenComparing(SellingPlanOption::value))
                        .toList();
    }
}
