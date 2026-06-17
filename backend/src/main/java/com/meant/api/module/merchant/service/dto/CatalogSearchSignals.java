package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CatalogSearchSignals(
        @JsonProperty("dev.ucp.buyer_ip")
        String buyerIp,

        @JsonProperty("dev.ucp.user_agent")
        String userAgent
) {
}
