package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.MerchantIdentityNamespace;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantIdentity;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.repository.MerchantIdentityRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.MerchantIdentityResolution;
import com.meant.api.module.merchant.service.dto.ResolvedMerchantIdentityClaim;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantIdentityPersistenceService {

    private final MerchantRepository merchantRepository;
    private final MerchantIdentityRepository merchantIdentityRepository;

    public Optional<Merchant> findOwner(MerchantRaw source, MerchantIdentityResolution resolution) {
        Map<UUID, Merchant> owners = new LinkedHashMap<>();
        addOwner(owners, source.getMerchant());
        merchantRepository.findByDomain(resolution.canonicalDomain())
                .ifPresent(merchant -> addOwner(owners, merchant));
        Map<IdentityClaimKey, MerchantIdentity> existingClaims = loadExistingClaims(resolution.claims());
        resolution.claims().stream()
                .map(IdentityClaimKey::from)
                .map(existingClaims::get)
                .filter(Objects::nonNull)
                .map(MerchantIdentity::getMerchant)
                .forEach(merchant -> addOwner(owners, merchant));
        if (owners.size() > 1) {
            throw new MerchantEnrichmentException(
                    "Merchant identity evidence belongs to more than one merchant"
            );
        }
        return owners.values().stream().findFirst();
    }

    public void linkSourceAndPersistClaims(
            MerchantRaw source,
            Merchant merchant,
            MerchantIdentityResolution resolution,
            Instant verifiedAt
    ) {
        source.linkMerchant(merchant);
        Map<IdentityClaimKey, MerchantIdentity> existingClaims = loadExistingClaims(resolution.claims());
        Map<IdentityClaimKey, ResolvedMerchantIdentityClaim> missingClaims = new LinkedHashMap<>();
        for (ResolvedMerchantIdentityClaim claim : resolution.claims()) {
            IdentityClaimKey key = IdentityClaimKey.from(claim);
            MerchantIdentity existing = existingClaims.get(key);
            if (existing != null) {
                if (!existing.getMerchant().getId().equals(merchant.getId())) {
                    throw new MerchantEnrichmentException("Merchant identity claim is already owned");
                }
                continue;
            }
            missingClaims.putIfAbsent(key, claim);
        }
        if (!missingClaims.isEmpty()) {
            List<MerchantIdentity> identities = new ArrayList<>(missingClaims.size());
            missingClaims.values().forEach(claim -> identities.add(MerchantIdentity.builder()
                    .merchant(merchant)
                    .namespace(claim.namespace())
                    .normalizedValue(claim.normalizedValue())
                    .role(claim.role())
                    .source(source.getSource())
                    .verifiedAt(verifiedAt)
                    .build()));
            merchantIdentityRepository.saveAll(identities);
        }
    }

    private Map<IdentityClaimKey, MerchantIdentity> loadExistingClaims(
            List<ResolvedMerchantIdentityClaim> claims
    ) {
        if (claims.isEmpty()) {
            return Map.of();
        }
        Set<MerchantIdentityNamespace> namespaces = new LinkedHashSet<>();
        Set<String> normalizedValues = new LinkedHashSet<>();
        claims.forEach(claim -> {
            namespaces.add(claim.namespace());
            normalizedValues.add(claim.normalizedValue());
        });
        Set<IdentityClaimKey> requestedKeys = new LinkedHashSet<>();
        claims.stream().map(IdentityClaimKey::from).forEach(requestedKeys::add);

        Map<IdentityClaimKey, MerchantIdentity> identitiesByClaim = new LinkedHashMap<>();
        merchantIdentityRepository.findByNamespaceInAndNormalizedValueIn(namespaces, normalizedValues)
                .forEach(identity -> {
                    IdentityClaimKey key = IdentityClaimKey.from(identity);
                    if (requestedKeys.contains(key)) {
                        identitiesByClaim.put(key, identity);
                    }
                });
        return identitiesByClaim;
    }

    private record IdentityClaimKey(MerchantIdentityNamespace namespace, String normalizedValue) {

        private static IdentityClaimKey from(ResolvedMerchantIdentityClaim claim) {
            return new IdentityClaimKey(claim.namespace(), claim.normalizedValue());
        }

        private static IdentityClaimKey from(MerchantIdentity identity) {
            return new IdentityClaimKey(identity.getNamespace(), identity.getNormalizedValue());
        }
    }

    private void addOwner(Map<UUID, Merchant> owners, Merchant merchant) {
        if (merchant != null) {
            owners.putIfAbsent(merchant.getId(), merchant);
        }
    }
}
