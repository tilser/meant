package com.meant.api.module.user.service;

import com.meant.api.common.util.CountryCodeNormalizer;
import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.repository.UserSavedProductRepository;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.RemoveSavedProductCommand;
import com.meant.api.module.user.service.command.SaveUserProductCommand;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
import com.meant.api.module.catalog.service.dto.CatalogPayloadClass;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.CatalogRetentionDecision;
import com.meant.api.module.catalog.service.dto.CatalogRetentionMode;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.CatalogDataUsePolicyResolver;
import com.meant.api.module.catalog.service.CatalogProductRehydrationService;
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
    private final UserSettingsService userSettingsService;
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
        CatalogRehydrationContext context = context(userSettingsService.get(profileCommand));
        List<UserSavedProduct> saved = persistenceService.findVerified(
                query.userId(),
                userCollectionProperties.savedProducts().quota()
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
        int limit = boundedLimit(query.limit(), userCollectionProperties.savedProducts().maxLimit());
        long offset = (long) query.page() * limit;
        Map<UserSavedProduct, CatalogProductReference> visibleReferences = references.entrySet().stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<CatalogProductReference, CatalogProductRehydrationResult> results = new LinkedHashMap<>();
        if (!visibleReferences.isEmpty()) {
            rehydrationService.rehydrate(
                    List.copyOf(visibleReferences.values()),
                    context
            ).forEach(result -> results.put(result.reference(), result));
        }
        return visibleReferences.entrySet().stream()
                .map(entry -> resultMapper.result(entry.getKey(), results.get(entry.getValue()), context))
                .toList();
    }

    public UserSavedProductResult save(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SaveUserProductCommand command
    ) {
        validateUser(profileCommand, command.userId());
        CatalogRehydrationContext context = context(userSettingsService.get(profileCommand));
        Instant now = Instant.now();
        CatalogProductReference requested = referenceResolver.resolve(command, now);
        CatalogProductRehydrationResult rehydrated = rehydrationService.rehydrate(
                requested,
                context
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
        return resultMapper.result(saved, rehydrated, context);
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

    private CatalogRehydrationContext context(UserSettingsResult settings) {
        String countryCode = settings == null || settings.location() == null
                ? null
                : CountryCodeNormalizer.normalizeAlpha2(settings.location().code());
        return new CatalogRehydrationContext(countryCode, null);
    }

    private void validateUser(EnsureUserProfileCommand profileCommand, UUID userId) {
        if (!profileCommand.id().equals(userId)) {
            throw UserException.forbidden("Saved product user does not match authenticated user");
        }
    }
}
