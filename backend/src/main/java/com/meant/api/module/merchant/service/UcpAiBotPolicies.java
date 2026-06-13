package com.meant.api.module.merchant.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Map;

record UcpAiBotPolicies(Map<String, Boolean> policies) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    UcpAiBotPolicies {
        policies = Map.copyOf(policies);
    }

    @JsonValue
    Map<String, Boolean> asJson() {
        return policies;
    }
}
