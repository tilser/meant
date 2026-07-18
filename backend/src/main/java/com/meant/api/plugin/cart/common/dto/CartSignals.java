package com.meant.api.plugin.cart.common.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CartSignals(
        @JsonProperty("dev.ucp.buyer_ip") String buyerIp,
        @JsonProperty("dev.ucp.user_agent") String userAgent,
        @JsonIgnore Map<String, JsonNode> extensions
) {
    public CartSignals(String buyerIp, String userAgent) {
        this(buyerIp, userAgent, null);
    }

    public CartSignals {
        extensions = extensions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extensions);
    }

    public boolean empty() {
        return buyerIp == null && userAgent == null && extensions.isEmpty();
    }

    @JsonAnySetter
    public void putExtension(String name, JsonNode value) {
        extensions.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, JsonNode> extensionValues() {
        return extensions;
    }
}
