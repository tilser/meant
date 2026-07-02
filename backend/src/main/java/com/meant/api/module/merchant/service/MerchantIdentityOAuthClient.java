package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantIdentityLinkException;
import com.meant.api.module.merchant.properties.MerchantIdentityLinkingProperties;
import com.meant.api.module.merchant.service.dto.MerchantIdentityAuthorizationServerMetadata;
import com.meant.api.module.merchant.service.dto.MerchantIdentityTokenResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class MerchantIdentityOAuthClient {

    private static final String WELL_KNOWN_AUTHORIZATION_SERVER = "/.well-known/oauth-authorization-server";
    private static final String WELL_KNOWN_OPENID_CONFIGURATION = "/.well-known/openid-configuration";
    private static final String CODE_CHALLENGE_METHOD = "S256";
    private static final String CLIENT_SECRET_BASIC = "client_secret_basic";
    private static final String CLIENT_AUTH_NONE = "none";

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
        String discoveryBase = discoveryBase(ucpUrl);
        String metadataUrl = discoveryBase + WELL_KNOWN_AUTHORIZATION_SERVER;
        try {
            return fetchMetadata(metadataUrl, discoveryBase);
        } catch (HttpClientErrorException.NotFound exception) {
            return discoverFromOpenIdConfiguration(discoveryBase, exception);
        } catch (RestClientException exception) {
            throw MerchantIdentityLinkException.upstream("Could not discover merchant identity OAuth metadata", exception);
        }
    }

    private MerchantIdentityAuthorizationServerMetadata discoverFromOpenIdConfiguration(
            String discoveryBase,
            HttpClientErrorException.NotFound primaryNotFound
    ) {
        try {
            return fetchMetadata(discoveryBase + WELL_KNOWN_OPENID_CONFIGURATION, discoveryBase);
        } catch (RestClientException exception) {
            exception.addSuppressed(primaryNotFound);
            throw MerchantIdentityLinkException.upstream(
                    "Could not discover merchant identity OAuth metadata from OIDC fallback",
                    exception
            );
        }
    }

    private MerchantIdentityAuthorizationServerMetadata fetchMetadata(String metadataUrl, String discoveryBase) {
        MerchantIdentityAuthorizationServerMetadata metadata = restClient
                .get()
                .uri(metadataUrl)
                .retrieve()
                .body(MerchantIdentityAuthorizationServerMetadata.class);
        validateMetadata(metadata, discoveryBase);
        return metadata;
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
        form.add("code_verifier", codeVerifier);
        return postToken(metadata, form);
    }

    public MerchantIdentityTokenResponse refreshToken(
            MerchantIdentityAuthorizationServerMetadata metadata,
            String refreshToken
    ) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return postToken(metadata, form);
    }

    public void revokeToken(MerchantIdentityAuthorizationServerMetadata metadata, String token) {
        if (!StringUtils.hasText(metadata.revocationEndpoint()) || !StringUtils.hasText(token)) {
            return;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        String clientAuthentication = clientAuthentication(metadata);
        addFormClientAuthentication(form, clientAuthentication);
        try {
            restClient
                    .post()
                    .uri(metadata.revocationEndpoint())
                    .headers(headers -> clientAuthentication(headers, clientAuthentication))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            // Local revocation still removes Meant's ability to use the merchant account.
        }
    }

    private MerchantIdentityTokenResponse postToken(
            MerchantIdentityAuthorizationServerMetadata metadata,
            MultiValueMap<String, String> form
    ) {
        String clientAuthentication = clientAuthentication(metadata);
        addFormClientAuthentication(form, clientAuthentication);
        try {
            MerchantIdentityTokenResponse response = restClient
                    .post()
                    .uri(metadata.tokenEndpoint())
                    .headers(headers -> clientAuthentication(headers, clientAuthentication))
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

    private void addFormClientAuthentication(MultiValueMap<String, String> form, String clientAuthentication) {
        if (CLIENT_AUTH_NONE.equals(clientAuthentication) && !form.containsKey("client_id")) {
            form.set("client_id", properties.clientId());
        }
    }

    private void clientAuthentication(HttpHeaders headers, String clientAuthentication) {
        if (CLIENT_AUTH_NONE.equals(clientAuthentication)) {
            return;
        }
        String credentials = properties.clientId() + ":" + properties.clientSecret();
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + basic);
    }

    private String clientAuthentication(MerchantIdentityAuthorizationServerMetadata metadata) {
        List<String> methods = metadata.tokenEndpointAuthMethodsSupported();
        if (StringUtils.hasText(properties.clientSecret()) && methods.contains(CLIENT_SECRET_BASIC)) {
            return CLIENT_SECRET_BASIC;
        }
        if (methods.contains(CLIENT_AUTH_NONE)) {
            return CLIENT_AUTH_NONE;
        }
        throw new MerchantIdentityLinkException(
                "Merchant identity metadata does not advertise a supported token endpoint auth method"
        );
    }

    private void validateMetadata(MerchantIdentityAuthorizationServerMetadata metadata, String discoveryBase) {
        if (metadata == null) {
            throw new MerchantIdentityLinkException("Merchant identity metadata was empty");
        }
        requireText(metadata.issuer(), "issuer");
        if (!metadata.issuer().equals(discoveryBase)) {
            throw new MerchantIdentityLinkException("Merchant identity metadata issuer does not match discovery base");
        }
        requireText(metadata.authorizationEndpoint(), "authorization_endpoint");
        requireText(metadata.tokenEndpoint(), "token_endpoint");
        requireList(metadata.scopesSupported(), "scopes_supported");
        if (!requireList(metadata.codeChallengeMethodsSupported(), "code_challenge_methods_supported")
                .contains(CODE_CHALLENGE_METHOD)) {
            throw new MerchantIdentityLinkException("Merchant identity metadata does not support PKCE S256");
        }
        requireList(metadata.tokenEndpointAuthMethodsSupported(), "token_endpoint_auth_methods_supported");
        if (!Boolean.TRUE.equals(metadata.authorizationResponseIssParameterSupported())) {
            throw new MerchantIdentityLinkException("Merchant identity metadata does not advertise authorization iss support");
        }
        clientAuthentication(metadata);
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new MerchantIdentityLinkException("Merchant identity metadata is missing " + fieldName);
        }
        return value;
    }

    private List<String> requireList(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new MerchantIdentityLinkException("Merchant identity metadata is missing " + fieldName);
        }
        return values;
    }

    private String discoveryBase(String ucpUrl) {
        URI uri = URI.create(ucpUrl);
        if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getAuthority())) {
            throw new MerchantIdentityLinkException("Merchant UCP URL must be absolute");
        }
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
