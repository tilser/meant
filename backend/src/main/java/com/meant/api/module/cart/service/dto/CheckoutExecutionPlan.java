package com.meant.api.module.cart.service.dto;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.merchant.constant.CapabilityIneligibilityReason;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import java.util.List;
import java.util.Objects;

public record CheckoutExecutionPlan(
        CheckoutNextAction nextAction,
        CommerceExecutionRail selectedRail,
        List<CapabilityIneligibilityReason> ineligibilityReasons
) {

    public CheckoutExecutionPlan {
        Objects.requireNonNull(nextAction, "nextAction must not be null");
        Objects.requireNonNull(selectedRail, "selectedRail must not be null");
        ineligibilityReasons = ineligibilityReasons == null ? List.of() : List.copyOf(ineligibilityReasons);
    }
}
