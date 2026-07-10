package com.meant.api.plugin.transport.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ShopifyTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn,
        String scope
) {

    @Override
    public String toString() {
        return "ShopifyTokenResponse[accessToken=[redacted], tokenType=%s, expiresIn=%s, scope=%s]"
                .formatted(tokenType, expiresIn, scope);
    }
}
