package com.meant.api.plugin.transport.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ShopifyTokenRequest(
        @JsonProperty("client_id") String clientId,
        @JsonProperty("client_secret") String clientSecret,
        @JsonProperty("grant_type") String grantType
) {

    @Override
    public String toString() {
        return "ShopifyTokenRequest[clientId=%s, clientSecret=[redacted], grantType=%s]"
                .formatted(clientId, grantType);
    }
}
