package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantIdentityLinkException;
import com.meant.api.module.merchant.properties.MerchantIdentityLinkingProperties;
import com.meant.api.module.merchant.service.dto.MerchantIdentityAuthorizationServerMetadata;
import com.meant.api.module.merchant.service.dto.MerchantIdentityTokenResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class MerchantIdentityOAuthClient {

    private static final String WELL_KNOWN_AUTHORIZATION_SERVER = "/.well-known/oauth-authorization-server";

    private final RestClient restClient;
    private final MerchantIdentityLinkingProperties properties;

    public MerchantIdentityOAuthClient(
            RestClient.Builder restClientBuilder,
            MerchantIdentityLinkingProperties properties
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public MerchantIdentityAuthorizationServerMetadata discover(String ucpUrl) {
        String metadataUrl = metadataUrl(ucpUrl);
        try {
            MerchantIdentityAuthorizationServerMetadata metadata = restClient
                    .get()
                    .uri(metadataUrl)
                    .retrieve()
                    .body(MerchantIdentityAuthorizationServerMetadata.class);
            if (metadata == null
                    || !StringUtils.hasText(metadata.authorizationEndpoint())
                    || !StringUtils.hasText(metadata.tokenEndpoint())) {
                throw new MerchantIdentityLinkException("Merchant identity metadata is missing OAuth endpoints");
            }
            return metadata;
        } catch (RestClientException exception) {
            throw MerchantIdentityLinkException.upstream("Could not discover merchant identity OAuth metadata", exception);
        }
    }

    public MerchantIdentityTokenResponse exchangeAuthorizationCode(
            MerchantIdentityAuthorizationServerMetadata metadata,
            String code,
            String codeVerifier
    ) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri().toString());
        form.add("client_id", properties.clientId());
        form.add("code_verifier", codeVerifier);
        return postToken(metadata.tokenEndpoint(), form);
    }

    public MerchantIdentityTokenResponse refreshToken(
            MerchantIdentityAuthorizationServerMetadata metadata,
            String refreshToken
    ) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", properties.clientId());
        return postToken(metadata.tokenEndpoint(), form);
    }

    public void revokeToken(MerchantIdentityAuthorizationServerMetadata metadata, String token) {
        if (!StringUtils.hasText(metadata.revocationEndpoint()) || !StringUtils.hasText(token)) {
            return;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        form.add("client_id", properties.clientId());
        try {
            restClient
                    .post()
                    .uri(metadata.revocationEndpoint())
                    .headers(headers -> clientAuthentication(headers, form))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            // Local revocation still removes Meant's ability to use the merchant account.
        }
    }

    private MerchantIdentityTokenResponse postToken(String tokenEndpoint, MultiValueMap<String, String> form) {
        try {
            MerchantIdentityTokenResponse response = restClient
                    .post()
                    .uri(tokenEndpoint)
                    .headers(headers -> clientAuthentication(headers, form))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MerchantIdentityTokenResponse.class);
            if (response == null || !StringUtils.hasText(response.accessToken())) {
                throw new MerchantIdentityLinkException("Merchant identity token response is missing an access token");
            }
            return response;
        } catch (RestClientException exception) {
            throw MerchantIdentityLinkException.upstream("Could not exchange merchant identity OAuth token", exception);
        }
    }

    private void clientAuthentication(HttpHeaders headers, MultiValueMap<String, String> form) {
        if (!StringUtils.hasText(properties.clientSecret())) {
            return;
        }
        String credentials = properties.clientId() + ":" + properties.clientSecret();
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + basic);
    }

    private String metadataUrl(String ucpUrl) {
        URI uri = URI.create(ucpUrl);
        URI origin = URI.create(uri.getScheme() + "://" + uri.getAuthority());
        return origin.resolve(WELL_KNOWN_AUTHORIZATION_SERVER).toString();
    }
}
