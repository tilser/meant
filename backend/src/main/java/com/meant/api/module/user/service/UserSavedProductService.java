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
import com.meant.api.module.user.service.query.GetSavedProductQuery;
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
import com.meant.api.module.catalog.service.dto.CatalogPayloadClass;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRetentionDecision;
import com.meant.api.module.catalog.service.dto.CatalogRetentionMode;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.CatalogDataUsePolicyResolver;
import com.meant.api.module.catalog.service.CatalogProductDetailService;
import com.meant.api.module.catalog.service.CatalogProductRehydrationService;
import com.meant.api.module.catalog.service.CatalogPurchaseReferencePolicyResolver;
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
    private final CatalogProductDetailService detailService;
    private final CatalogPurchaseReferencePolicyResolver purchaseReferencePolicyResolver;
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

    public UserSavedProductResult get(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid GetSavedProductQuery query
    ) {
        validateUser(profileCommand, query.userId());
        UserSavedProduct saved = persistenceService.findVerified(query.userId(), query.productKey())
                .orElseThrow(() -> UserException.notFound("Saved product was not found"));
        CatalogProductReference reference = resultMapper.reference(saved);
        if (reference == null || !retentionPolicyMatches(saved, reference)) {
            throw UserException.notFound("Saved product reference is no longer available");
        }
        CatalogRehydrationContext context = context(userSettingsService.get(profileCommand));
        return resultMapper.detailResult(saved, detailService.getDetails(reference, context), context);
    }

    public UserSavedProductResult save(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SaveUserProductCommand command
    ) {
        validateUser(profileCommand, command.userId());
        Instant now = Instant.now();
        CatalogProductReference resolved = referenceResolver.resolve(command, now);
        validateDurablePurchaseReference(resolved);
        CatalogRetentionDecision policy = policyResolver.resolve(
                resolved.discoverySource(),
                CatalogPayloadClass.SAVED_INTERACTION
        );
        if (policy.mode() != CatalogRetentionMode.DURABLE_IDENTIFIERS_ONLY) {
            throw new UserException("Provider policy does not permit a durable saved-product reference");
        }
        UserSavedProduct saved = persistenceService.save(command, resolved, policy.policyKey(), now);
        return resultMapper.result(saved, null, null);
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
        return new CatalogRehydrationContext(
                countryCode,
                null,
                settings == null ? null : settings.currency());
    }

    private boolean retentionPolicyMatches(
            UserSavedProduct saved,
            CatalogProductReference reference
    ) {
        CatalogRetentionDecision policy = policyResolver.resolve(
                reference.discoverySource(), CatalogPayloadClass.SAVED_INTERACTION);
        return policy.mode() == CatalogRetentionMode.DURABLE_IDENTIFIERS_ONLY
                && policy.policyKey().equals(saved.getRetentionPolicyKey());
    }

    private void validateUser(EnsureUserProfileCommand profileCommand, UUID userId) {
        if (!profileCommand.id().equals(userId)) {
            throw UserException.forbidden("Saved product user does not match authenticated user");
        }
    }

    private void validateDurablePurchaseReference(CatalogProductReference reference) {
        if (reference.externalVariantReference() == null) {
            throw new UserException("Saved product requires an exact purchasable variant");
        }
        boolean locallyRouted = reference.localMerchantId() != null || reference.localRouting() != null;
        boolean externallyRouted = reference.externalMerchantReference() != null;
        if (!locallyRouted && !externallyRouted) {
            throw new UserException("Saved product requires a verified merchant routing identity");
        }
        if (!purchaseReferencePolicyResolver.allows(reference)) {
            throw new UserException("Provider policy requires additional verified purchase routing data");
        }
    }
}
