package com.meant.api.module.user.service;

import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.repository.UserSavedProductRepository;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.RemoveSavedProductCommand;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
import com.meant.api.plugin.catalog.common.dto.CatalogPayloadClass;
import com.meant.api.plugin.catalog.common.dto.CatalogProductReference;
import com.meant.api.plugin.catalog.common.dto.CatalogProductRehydrationResult;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationContext;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationStatus;
import com.meant.api.plugin.catalog.common.dto.CatalogRetentionDecision;
import com.meant.api.plugin.catalog.common.dto.CatalogRetentionMode;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.service.CatalogDataUsePolicyResolver;
import com.meant.api.plugin.catalog.common.service.CatalogProductRehydrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserSavedProductService {
    private final UserService userService;
    private final UserSavedProductRepository userSavedProductRepository;
    private final UserCollectionProperties userCollectionProperties;
    private final UserSavedProductReferenceResolver referenceResolver;
    private final CatalogDataUsePolicyResolver policyResolver;
    private final CatalogProductRehydrationService rehydrationService;
    private final UserSavedProductPersistenceService persistenceService;
    private final UserSavedProductResultMapper resultMapper;

    public List<UserSavedProductResult> list(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid ListSavedProductsQuery query
    ) {
        validateUser(profileCommand, query.userId());
        userService.ensureProfile(profileCommand);
        List<UserSavedProduct> saved = persistenceService.findVerified(
                query.userId(),
                query.page(),
                boundedLimit(query.limit(), userCollectionProperties.savedProducts().maxLimit())
        );
        Map<UserSavedProduct, CatalogProductReference> references = new LinkedHashMap<>();
        for (UserSavedProduct entity : saved) {
            CatalogProductReference reference = resultMapper.reference(entity);
            if (reference != null) {
                references.put(entity, reference);
            }
        }
        Map<DiscoverySourceIdentity, CatalogRetentionDecision> policies =
                references.values().stream()
                        .map(CatalogProductReference::discoverySource)
                        .distinct()
                        .collect(Collectors.toMap(
                                source -> source,
                                source -> policyResolver.resolve(source, CatalogPayloadClass.SAVED_INTERACTION)
                        ));
        references.entrySet().removeIf(entry -> {
            CatalogRetentionDecision policy = policies.get(entry.getValue().discoverySource());
            return policy.mode() != CatalogRetentionMode.DURABLE_IDENTIFIERS_ONLY
                    || !policy.policyKey().equals(entry.getKey().getRetentionPolicyKey());
        });
        Map<CatalogProductReference, CatalogProductRehydrationResult> results = new LinkedHashMap<>();
        if (!references.isEmpty()) {
            rehydrationService.rehydrate(
                    List.copyOf(references.values()),
                    new CatalogRehydrationContext(null, null)
            ).forEach(result -> results.put(result.reference(), result));
        }
        return references.entrySet().stream()
                .map(entry -> resultMapper.result(entry.getKey(), results.get(entry.getValue())))
                .toList();
    }

    public UserSavedProductResult save(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SaveUserProductCommand command
    ) {
        validateUser(profileCommand, command.userId());
        userService.ensureProfile(profileCommand);
        Instant now = Instant.now();
        CatalogProductReference requested = referenceResolver.resolve(command, now);
        CatalogProductRehydrationResult rehydrated = rehydrationService.rehydrate(
                requested,
                new CatalogRehydrationContext(null, null)
        );
        if (rehydrated.status() != CatalogRehydrationStatus.FRESH || rehydrated.resolvedReference() == null) {
            throw new UserException("Saved product could not be verified from current provider facts");
        }
        CatalogProductReference verified = rehydrated.resolvedReference();
        CatalogRetentionDecision policy = policyResolver.resolve(
                verified.discoverySource(),
                CatalogPayloadClass.SAVED_INTERACTION
        );
        if (policy.mode() != CatalogRetentionMode.DURABLE_IDENTIFIERS_ONLY) {
            throw new UserException("Provider policy does not permit a durable saved-product reference");
        }
        UserSavedProduct saved = persistenceService.save(command, verified, policy.policyKey(), now);
        return resultMapper.result(saved, rehydrated);
    }

    @Transactional
    public void remove(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid RemoveSavedProductCommand command
    ) {
        validateUser(profileCommand, command.userId());
        userService.ensureProfile(profileCommand);
        userSavedProductRepository.deleteByUserIdAndProductKey(command.userId(), command.productKey());
    }

    private int boundedLimit(int limit, int maxLimit) {
        return Math.min(limit, maxLimit);
    }

    private void validateUser(EnsureUserProfileCommand profileCommand, UUID userId) {
        if (!profileCommand.id().equals(userId)) {
            throw UserException.forbidden("Saved product user does not match authenticated user");
        }
    }
}
