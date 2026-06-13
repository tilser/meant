package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Map;

public record UcpAiBotPolicies(@JsonValue Map<String, Boolean> policies) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public UcpAiBotPolicies {
        policies = Map.copyOf(policies);
    }

}
