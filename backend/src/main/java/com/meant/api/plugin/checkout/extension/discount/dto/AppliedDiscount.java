package com.meant.api.plugin.checkout.extension.discount.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AppliedDiscount(
        String code,
        String title,
        Integer amount,
        Boolean automatic,
        String method,
        Integer priority,
        Boolean provisional,
        String eligibility,
        List<DiscountAllocation> allocations
) {

    public AppliedDiscount {
        allocations = allocations == null ? List.of() : List.copyOf(allocations);
    }
}
