package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UcpMerchantImportPersistenceService {

    private static final int DOMAIN_UPDATE_BATCH_SIZE = 1_000;

    private final MerchantRawRepository merchantRawRepository;
    private final MerchantRepository merchantRepository;

    @Transactional
    public void saveImport(List<MerchantRaw> verifiedMerchants, Instant fetchedAt) {
        Set<String> seenDomains = verifiedMerchants.stream()
                .map(MerchantRaw::getDomain)
                .collect(Collectors.toSet());
        Map<String, MerchantRaw> existingByDomain = findExistingMerchants(seenDomains);

        List<MerchantRaw> merchantsToSave = verifiedMerchants.stream()
                .map(importedMerchant -> mergeImportedMerchant(
                        importedMerchant,
                        existingByDomain.get(importedMerchant.getDomain())
                ))
                .toList();

        merchantRawRepository.saveAll(merchantsToSave);
        markMissingMerchantsInactive(seenDomains, fetchedAt);
    }

    private Map<String, MerchantRaw> findExistingMerchants(Set<String> seenDomains) {
        if (seenDomains.isEmpty()) {
            return Map.of();
        }
        return merchantRawRepository.findByDomainIn(seenDomains).stream()
                .collect(Collectors.toMap(MerchantRaw::getDomain, Function.identity()));
    }

    private void markMissingMerchantsInactive(Set<String> seenDomains, Instant fetchedAt) {
        if (seenDomains.isEmpty()) {
            merchantRawRepository.markAllActiveInactive();
            merchantRepository.markAllActiveInactive(fetchedAt);
            return;
        }
        markRawDomainsInactive(missingDomains(merchantRawRepository.findActiveDomains(), seenDomains));
        markMerchantDomainsInactive(missingDomains(merchantRepository.findActiveDomains(), seenDomains), fetchedAt);
    }

    private List<String> missingDomains(List<String> activeDomains, Set<String> seenDomains) {
        return activeDomains.stream()
                .filter(domain -> !seenDomains.contains(domain))
                .toList();
    }

    private void markRawDomainsInactive(List<String> domains) {
        batches(domains).forEach(merchantRawRepository::markInactiveByDomainIn);
    }

    private void markMerchantDomainsInactive(List<String> domains, Instant fetchedAt) {
        batches(domains).forEach(batch -> merchantRepository.markInactiveByDomainIn(batch, fetchedAt));
    }

    private List<List<String>> batches(List<String> values) {
        return java.util.stream.IntStream.range(0, (values.size() + DOMAIN_UPDATE_BATCH_SIZE - 1) / DOMAIN_UPDATE_BATCH_SIZE)
                .mapToObj(batchIndex -> values.subList(
                        batchIndex * DOMAIN_UPDATE_BATCH_SIZE,
                        Math.min((batchIndex + 1) * DOMAIN_UPDATE_BATCH_SIZE, values.size())
                ))
                .toList();
    }

    private MerchantRaw mergeImportedMerchant(MerchantRaw importedMerchant, MerchantRaw existingMerchant) {
        if (existingMerchant == null) {
            return importedMerchant;
        }

        existingMerchant.updateFromImport(
                importedMerchant.getDatasetRowIdx(),
                importedMerchant.getStatus(),
                importedMerchant.getUcpUrl(),
                importedMerchant.getHttpStatus(),
                importedMerchant.getUcpVersion(),
                importedMerchant.isHasCheckout(),
                importedMerchant.isHasIdentityLinking(),
                importedMerchant.isHasCartManagement(),
                importedMerchant.isHasOrder(),
                importedMerchant.isHasPaymentToken(),
                importedMerchant.getCapabilityCount(),
                importedMerchant.getAiBotPolicies(),
                importedMerchant.getTransports(),
                importedMerchant.getLastCheckedAt(),
                importedMerchant.getLastSuccessAt(),
                importedMerchant.getFetchedAt(),
                importedMerchant.getSourceHash()
        );
        return existingMerchant;
    }
}
