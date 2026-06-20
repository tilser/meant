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
import com.meant.api.module.merchant.service.query.GetMerchantIdentityAccessTokenQuery;
import com.meant.api.module.merchant.service.query.ListMerchantIdentityLinksQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Validated
@RequiredArgsConstructor
public class MerchantIdentityLinkService {

    private static final String CODE_CHALLENGE_METHOD = "S256";

    private final MerchantRepository merchantRepository;
    private final MerchantIdentityLinkRepository merchantIdentityLinkRepository;
    private final MerchantIdentityOAuthClient merchantIdentityOAuthClient;
    private final MerchantIdentityTokenCipher merchantIdentityTokenCipher;
    private final MerchantIdentityLinkingProperties properties;
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

        List<String> scopes = properties.defaultScopes();
        String authorizationUrl = UriComponentsBuilder.fromUriString(metadata.authorizationEndpoint())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri().toString())
                .queryParam("scope", String.join(" ", scopes))
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge(codeVerifier))
                .queryParam("code_challenge_method", CODE_CHALLENGE_METHOD)
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
        if (StringUtils.hasText(command.issuer())
                && StringUtils.hasText(link.getIssuer())
                && !command.issuer().equals(link.getIssuer())) {
            throw new MerchantIdentityLinkException("Merchant identity link issuer mismatch");
        }

        MerchantIdentityAuthorizationServerMetadata metadata = merchantIdentityOAuthClient
                .discover(link.getMerchant().getUcpUrl());
        if (StringUtils.hasText(command.issuer())
                && StringUtils.hasText(metadata.issuer())
                && !command.issuer().equals(metadata.issuer())) {
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
        String refreshToken = StringUtils.hasText(response.refreshToken())
                ? response.refreshToken()
                : merchantIdentityTokenCipher.decrypt(
                        link.getRefreshTokenCiphertext(),
                        link.getUserId(),
                        link.getMerchant().getId());
        link.connect(
                merchantIdentityTokenCipher.encrypt(response.accessToken(), link.getUserId(), link.getMerchant().getId()),
                merchantIdentityTokenCipher.encrypt(refreshToken, link.getUserId(), link.getMerchant().getId()),
                StringUtils.hasText(response.tokenType()) ? response.tokenType() : "Bearer",
                StringUtils.hasText(response.scope()) ? response.scope() : String.join(" ", properties.defaultScopes()),
                hash(randomUrlToken()),
                merchantIdentityTokenCipher.encrypt(randomUrlToken(), link.getUserId(), link.getMerchant().getId()),
                response.expiresIn() == null ? null : now.plusSeconds(response.expiresIn()),
                now
        );
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
