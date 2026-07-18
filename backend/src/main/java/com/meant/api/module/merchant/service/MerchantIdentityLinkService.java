package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.MerchantIdentityLinkStatus;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantIdentityLink;
import com.meant.api.module.merchant.exception.MerchantIdentityLinkException;
import com.meant.api.module.merchant.properties.MerchantIdentityLinkingProperties;
import com.meant.api.module.merchant.repository.MerchantIdentityLinkRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.command.CompleteMerchantIdentityAuthorizationCommand;
import com.meant.api.module.merchant.service.command.RevokeMerchantIdentityLinkCommand;
import com.meant.api.module.merchant.service.command.StartMerchantIdentityAuthorizationCommand;
import com.meant.api.module.merchant.service.dto.MerchantIdentityAccessTokenResult;
import com.meant.api.module.merchant.service.dto.MerchantIdentityAuthorizationResult;
import com.meant.api.module.merchant.service.dto.MerchantIdentityAuthorizationServerMetadata;
import com.meant.api.module.merchant.service.dto.MerchantIdentityLinkResult;
import com.meant.api.module.merchant.service.dto.MerchantIdentityTokenResponse;
import com.meant.api.module.merchant.service.dto.UcpCapabilityDefinition;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.query.GetMerchantIdentityAccessTokenQuery;
import com.meant.api.module.merchant.service.query.ListMerchantIdentityLinksQuery;
import com.meant.api.plugin.transport.registry.CapabilityRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor
public class MerchantIdentityLinkService {

    private static final String IDENTITY_LINKING_CAPABILITY = "dev.ucp.common.identity_linking";
    private static final String CODE_CHALLENGE_METHOD = "S256";

    private final MerchantRepository merchantRepository;
    private final MerchantIdentityLinkRepository merchantIdentityLinkRepository;
    private final MerchantIdentityOAuthClient merchantIdentityOAuthClient;
    private final MerchantIdentityTokenCipher merchantIdentityTokenCipher;
    private final MerchantIdentityLinkingProperties properties;
    private final CapabilityRegistry capabilityRegistry;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public List<MerchantIdentityLinkResult> list(@NotNull @Valid ListMerchantIdentityLinksQuery query) {
        return merchantIdentityLinkRepository.findByUserIdOrderByUpdatedAtDesc(query.userId()).stream()
                .map(MerchantIdentityLinkResult::from)
                .toList();
    }

    @Transactional
    public MerchantIdentityAuthorizationResult startAuthorization(
            @NotNull @Valid StartMerchantIdentityAuthorizationCommand command
    ) {
        Merchant merchant = findIdentityLinkingMerchant(command.merchantId());
        MerchantIdentityAuthorizationServerMetadata metadata = merchantIdentityOAuthClient.discover(merchant.getUcpUrl());
        List<String> scopes = derivedScopes(merchant, metadata);
        String state = randomUrlToken();
        String codeVerifier = randomUrlToken();
        String stateHash = hash(state);
        Instant now = Instant.now();
        String codeVerifierCiphertext = merchantIdentityTokenCipher.encrypt(
                codeVerifier,
                command.userId(),
                command.merchantId());

        MerchantIdentityLink link = merchantIdentityLinkRepository
                .findByUserIdAndMerchantId(command.userId(), command.merchantId())
                .orElseGet(() -> MerchantIdentityLink.builder()
                        .userId(command.userId())
                        .createdAt(now)
                        .build());
        link.startAuthorization(
                merchant,
                stateHash,
                codeVerifierCiphertext,
                metadata.issuer(),
                now
        );
        merchantIdentityLinkRepository.save(link);

        UriComponentsBuilder authorizationUrlBuilder = UriComponentsBuilder.fromUriString(metadata.authorizationEndpoint())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri().toString())
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge(codeVerifier))
                .queryParam("code_challenge_method", CODE_CHALLENGE_METHOD);
        if (!scopes.isEmpty()) {
            authorizationUrlBuilder.queryParam("scope", String.join(" ", scopes));
        }
        String authorizationUrl = authorizationUrlBuilder
                .build()
                .encode()
                .toUriString();
        return new MerchantIdentityAuthorizationResult(command.merchantId(), authorizationUrl, state, scopes);
    }

    @Transactional
    public MerchantIdentityLinkResult completeAuthorization(
            @NotNull @Valid CompleteMerchantIdentityAuthorizationCommand command
    ) {
        MerchantIdentityLink link = merchantIdentityLinkRepository.findByStateHash(hash(command.state()))
                .orElseThrow(() -> MerchantIdentityLinkException.notFound("Merchant identity link state not found"));
        if (!link.getUserId().equals(command.userId())) {
            throw new MerchantIdentityLinkException("Merchant identity link state does not belong to this user");
        }
        if (!StringUtils.hasText(command.issuer())) {
            throw new MerchantIdentityLinkException("Merchant identity authorization response is missing issuer");
        }
        if (!StringUtils.hasText(link.getIssuer()) || !command.issuer().equals(link.getIssuer())) {
            throw new MerchantIdentityLinkException("Merchant identity link issuer mismatch");
        }

        MerchantIdentityAuthorizationServerMetadata metadata = merchantIdentityOAuthClient
                .discover(link.getMerchant().getUcpUrl());
        if (!command.issuer().equals(metadata.issuer())) {
            throw new MerchantIdentityLinkException("Merchant identity metadata issuer mismatch");
        }

        String codeVerifier = merchantIdentityTokenCipher.decrypt(
                link.getCodeVerifierCiphertext(),
                link.getUserId(),
                link.getMerchant().getId());
        MerchantIdentityTokenResponse response = merchantIdentityOAuthClient.exchangeAuthorizationCode(
                metadata,
                command.code(),
                codeVerifier
        );
        persistTokens(link, response, Instant.now());
        return MerchantIdentityLinkResult.from(link);
    }

    @Transactional
    public void revoke(@NotNull @Valid RevokeMerchantIdentityLinkCommand command) {
        merchantIdentityLinkRepository.findByUserIdAndMerchantId(command.userId(), command.merchantId())
                .ifPresent(link -> {
                    // Best-effort upstream revocation: if the merchant server is down or misconfigured,
                    // still delete the local link so the user is never stuck with an unremovable connection.
                    try {
                        MerchantIdentityAuthorizationServerMetadata metadata = merchantIdentityOAuthClient
                                .discover(link.getMerchant().getUcpUrl());
                        String refreshToken = merchantIdentityTokenCipher.decrypt(
                                link.getRefreshTokenCiphertext(),
                                command.userId(),
                                command.merchantId());
                        String accessToken = merchantIdentityTokenCipher.decrypt(
                                link.getAccessTokenCiphertext(),
                                command.userId(),
                                command.merchantId());
                        merchantIdentityOAuthClient.revokeToken(metadata, refreshToken);
                        merchantIdentityOAuthClient.revokeToken(metadata, accessToken);
                    } catch (RuntimeException exception) {
                        // Local deletion below still removes Meant's ability to use the merchant account.
                    }
                    merchantIdentityLinkRepository.delete(link);
                });
    }

    @Transactional
    public MerchantIdentityAccessTokenResult getAccessToken(@NotNull @Valid GetMerchantIdentityAccessTokenQuery query) {
        MerchantIdentityLink link = merchantIdentityLinkRepository
                .findByUserIdAndMerchantId(query.userId(), query.merchantId())
                .filter(candidate -> candidate.getStatus() == MerchantIdentityLinkStatus.CONNECTED)
                .orElseThrow(() -> MerchantIdentityLinkException.notFound("Merchant identity link not found"));
        if (shouldRefresh(link)) {
            // Upgrade to a pessimistic write lock and reload the row so concurrent reads serialize their
            // refreshes. entityManager.refresh acquires the lock AND overwrites the entity's in-memory
            // state from the database; a second findBy... would return the same stale first-level-cache
            // instance and defeat the double-check. Without this, two requests could refresh the same
            // rotating refresh token in parallel and the authorization server would reject (and possibly
            // revoke) the second grant.
            entityManager.refresh(link, LockModeType.PESSIMISTIC_WRITE);
            // Another request may have refreshed while we waited on the lock; re-check before refreshing.
            if (shouldRefresh(link)) {
                refresh(link);
            }
        }
        return new MerchantIdentityAccessTokenResult(
                merchantIdentityTokenCipher.decrypt(link.getAccessTokenCiphertext(), query.userId(), query.merchantId()),
                link.getTokenType(),
                link.getScope(),
                link.getExpiresAt()
        );
    }

    private Merchant findIdentityLinkingMerchant(java.util.UUID merchantId) {
        Merchant merchant = merchantRepository.findByIdAndActiveTrue(merchantId)
                .orElseThrow(() -> MerchantIdentityLinkException.notFound("Merchant not found: " + merchantId));
        if (merchant.getMerchantRaw() == null || !merchant.getMerchantRaw().isHasIdentityLinking()) {
            throw new MerchantIdentityLinkException("Merchant does not support identity linking");
        }
        return merchant;
    }

    private List<String> derivedScopes(Merchant merchant, MerchantIdentityAuthorizationServerMetadata metadata) {
        UcpProfile profile = parseProfile(merchant);
        List<String> declaredScopes = identityLinkingScopes(profile);
        if (declaredScopes.isEmpty()) {
            return List.of();
        }

        Set<String> negotiatedCapabilityPrefixes = negotiatedCapabilityPrefixes(profile);
        Set<String> intendedScopes = new LinkedHashSet<>(properties.defaultScopes());
        List<String> scopes = declaredScopes.stream()
                .filter(intendedScopes::contains)
                .filter(scope -> negotiatedCapabilityPrefixes.contains(scopeCapabilityPrefix(scope)))
                .distinct()
                .toList();
        rejectUnsupportedScopes(scopes, metadata);
        return scopes;
    }

    private UcpProfile parseProfile(Merchant merchant) {
        if (!StringUtils.hasText(merchant.getProfileRaw())) {
            throw new MerchantIdentityLinkException("Merchant identity linking config is missing from the UCP profile");
        }
        try {
            UcpProfile profile = objectMapper.readValue(merchant.getProfileRaw(), UcpProfile.class);
            if (profile == null) {
                throw new MerchantIdentityLinkException("Merchant UCP profile was empty");
            }
            return profile;
        } catch (JacksonException exception) {
            throw new MerchantIdentityLinkException("Merchant UCP profile could not be parsed", exception);
        }
    }

    private List<String> identityLinkingScopes(UcpProfile profile) {
        return identityLinkingDefinitions(profile).stream()
                .map(UcpCapabilityDefinition::config)
                .filter(config -> config != null)
                .map(config -> config.get("scopes"))
                .filter(scopes -> scopes != null && scopes.isObject())
                .flatMap(scopes -> scopes.properties().stream())
                .map(Map.Entry::getKey)
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .distinct()
                .toList();
    }

    private List<UcpCapabilityDefinition> identityLinkingDefinitions(UcpProfile profile) {
        List<UcpCapabilityDefinition> identityDefinitions = safeCapabilityMap(profile).get(IDENTITY_LINKING_CAPABILITY);
        if (identityDefinitions != null && !identityDefinitions.isEmpty()) {
            return identityDefinitions.stream()
                    .filter(definition -> definition != null)
                    .toList();
        }
        return safeCapabilityMap(profile).values().stream()
                .filter(capabilityDefinitions -> capabilityDefinitions != null)
                .flatMap(List::stream)
                .filter(definition -> definition != null && IDENTITY_LINKING_CAPABILITY.equals(definition.id()))
                .toList();
    }

    private Set<String> negotiatedCapabilityPrefixes(UcpProfile profile) {
        Set<String> merchantPrefixes = new LinkedHashSet<>();
        safeCapabilityMap(profile).keySet().stream()
                .filter(capability -> !IDENTITY_LINKING_CAPABILITY.equals(capability))
                .forEach(capability -> merchantPrefixes.addAll(capabilityPrefixes(capability)));

        Set<String> platformPrefixes = new LinkedHashSet<>();
        capabilityRegistry.capabilities().stream()
                .map(capability -> capability.id().value())
                .forEach(capability -> platformPrefixes.addAll(capabilityPrefixes(capability)));

        merchantPrefixes.retainAll(platformPrefixes);
        return merchantPrefixes;
    }

    private Set<String> capabilityPrefixes(String capability) {
        if (!StringUtils.hasText(capability)) {
            return Set.of();
        }
        String normalized = capability.trim();
        Set<String> prefixes = new LinkedHashSet<>();
        prefixes.add(normalized);
        String[] parts = normalized.split("\\.");
        if (parts.length >= 5) {
            prefixes.add(normalized.substring(0, normalized.lastIndexOf('.')));
        }
        return prefixes;
    }

    private String scopeCapabilityPrefix(String scope) {
        int separator = scope.indexOf(':');
        if (separator <= 0 || separator == scope.length() - 1) {
            throw new MerchantIdentityLinkException("Merchant identity linking scope is invalid: " + scope);
        }
        return scope.substring(0, separator);
    }

    private void rejectUnsupportedScopes(
            List<String> scopes,
            MerchantIdentityAuthorizationServerMetadata metadata
    ) {
        List<String> unsupportedScopes = scopes.stream()
                .filter(scope -> !metadata.scopesSupported().contains(scope))
                .toList();
        if (!unsupportedScopes.isEmpty()) {
            throw new MerchantIdentityLinkException(
                    "Merchant identity metadata does not support requested scopes: "
                            + String.join(", ", unsupportedScopes)
            );
        }
    }

    private Map<String, List<UcpCapabilityDefinition>> safeCapabilityMap(UcpProfile profile) {
        return profile.capabilities() == null ? Map.of() : profile.capabilities();
    }

    private boolean shouldRefresh(MerchantIdentityLink link) {
        return link.getExpiresAt() != null
                && link.getExpiresAt().minus(properties.refreshSkew()).isBefore(Instant.now())
                && StringUtils.hasText(link.getRefreshTokenCiphertext());
    }

    private void refresh(MerchantIdentityLink link) {
        MerchantIdentityAuthorizationServerMetadata metadata = merchantIdentityOAuthClient
                .discover(link.getMerchant().getUcpUrl());
        String refreshToken = merchantIdentityTokenCipher.decrypt(
                link.getRefreshTokenCiphertext(),
                link.getUserId(),
                link.getMerchant().getId());
        MerchantIdentityTokenResponse response = merchantIdentityOAuthClient.refreshToken(metadata, refreshToken);
        persistTokens(link, response, Instant.now());
    }

    private void persistTokens(MerchantIdentityLink link, MerchantIdentityTokenResponse response, Instant now) {
        String refreshToken = refreshToken(link, response);
        link.connect(
                merchantIdentityTokenCipher.encrypt(response.accessToken(), link.getUserId(), link.getMerchant().getId()),
                StringUtils.hasText(refreshToken)
                        ? merchantIdentityTokenCipher.encrypt(refreshToken, link.getUserId(), link.getMerchant().getId())
                        : null,
                StringUtils.hasText(response.tokenType()) ? response.tokenType() : "Bearer",
                StringUtils.hasText(response.scope()) ? response.scope() : String.join(" ", properties.defaultScopes()),
                hash(randomUrlToken()),
                merchantIdentityTokenCipher.encrypt(randomUrlToken(), link.getUserId(), link.getMerchant().getId()),
                response.expiresIn() == null ? null : now.plusSeconds(response.expiresIn()),
                now
        );
    }

    private String refreshToken(MerchantIdentityLink link, MerchantIdentityTokenResponse response) {
        if (StringUtils.hasText(response.refreshToken())) {
            return response.refreshToken();
        }
        if (!StringUtils.hasText(link.getRefreshTokenCiphertext())) {
            return null;
        }
        return merchantIdentityTokenCipher.decrypt(
                link.getRefreshTokenCiphertext(),
                link.getUserId(),
                link.getMerchant().getId());
    }

    private String randomUrlToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String codeChallenge(String codeVerifier) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256Bytes(codeVerifier));
    }

    private String hash(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256Bytes(value));
    }

    private byte[] sha256Bytes(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw MerchantIdentityLinkException.upstream("Could not hash merchant identity OAuth value", exception);
        }
    }
}
