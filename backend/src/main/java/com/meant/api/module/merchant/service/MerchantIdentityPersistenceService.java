package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantIdentity;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.repository.MerchantIdentityRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.MerchantIdentityResolution;
import com.meant.api.module.merchant.service.dto.ResolvedMerchantIdentityClaim;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
        resolution.claims().forEach(claim -> merchantIdentityRepository
                .findByNamespaceAndNormalizedValue(claim.namespace(), claim.normalizedValue())
                .map(MerchantIdentity::getMerchant)
                .ifPresent(merchant -> addOwner(owners, merchant)));
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
        resolution.claims().forEach(claim -> persistClaim(source, merchant, claim, verifiedAt));
    }

    private void persistClaim(
            MerchantRaw source,
            Merchant merchant,
            ResolvedMerchantIdentityClaim claim,
            Instant verifiedAt
    ) {
        Optional<MerchantIdentity> existing = merchantIdentityRepository.findByNamespaceAndNormalizedValue(
                claim.namespace(),
                claim.normalizedValue()
        );
        if (existing.isPresent()) {
            if (!existing.get().getMerchant().getId().equals(merchant.getId())) {
                throw new MerchantEnrichmentException("Merchant identity claim is already owned");
            }
            return;
        }
        merchantIdentityRepository.save(MerchantIdentity.builder()
                .merchant(merchant)
                .namespace(claim.namespace())
                .normalizedValue(claim.normalizedValue())
                .role(claim.role())
                .source(source.getSource())
                .verifiedAt(verifiedAt)
                .build());
    }

    private void addOwner(Map<UUID, Merchant> owners, Merchant merchant) {
        if (merchant != null) {
            owners.putIfAbsent(merchant.getId(), merchant);
        }
    }
}
