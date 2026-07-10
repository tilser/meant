package com.meant.api.module.cart.service.dto;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.merchant.constant.CapabilityIneligibilityReason;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import java.util.List;

public record CheckoutExecutionPlan(
        CheckoutNextAction nextAction,
        CommerceExecutionRail selectedRail,
        List<CapabilityIneligibilityReason> ineligibilityReasons
) {

    public CheckoutExecutionPlan {
        ineligibilityReasons = ineligibilityReasons == null ? List.of() : List.copyOf(ineligibilityReasons);
    }
}
