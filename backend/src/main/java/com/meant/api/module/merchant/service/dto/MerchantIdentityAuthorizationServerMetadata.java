package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MerchantIdentityAuthorizationServerMetadata(
        String issuer,
        @JsonProperty("authorization_endpoint") String authorizationEndpoint,
        @JsonProperty("token_endpoint") String tokenEndpoint,
        @JsonProperty("revocation_endpoint") String revocationEndpoint,
        @JsonProperty("scopes_supported") List<String> scopesSupported,
        @JsonProperty("code_challenge_methods_supported") List<String> codeChallengeMethodsSupported,
        @JsonProperty("token_endpoint_auth_methods_supported") List<String> tokenEndpointAuthMethodsSupported,
        @JsonProperty("authorization_response_iss_parameter_supported") Boolean authorizationResponseIssParameterSupported
) {
}
