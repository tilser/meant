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

    private final MerchantRawRepository merchantRawRepository;
    private final MerchantRepository merchantRepository;

    @Transactional
    public void saveImport(List<MerchantRaw> verifiedMerchants, Instant fetchedAt) {
        Set<String> seenDomains = verifiedMerchants.stream()
                .map(MerchantRaw::getDomain)
                .collect(Collectors.toSet());
        Map<String, MerchantRaw> existingByDomain = merchantRawRepository.findByDomainIn(seenDomains).stream()
                .collect(Collectors.toMap(MerchantRaw::getDomain, Function.identity()));

        List<MerchantRaw> merchantsToSave = verifiedMerchants.stream()
                .map(importedMerchant -> mergeImportedMerchant(
                        importedMerchant,
                        existingByDomain.get(importedMerchant.getDomain())
                ))
                .toList();

        merchantRawRepository.saveAll(merchantsToSave);
        List<String> inactiveDomains = merchantRawRepository.findAll().stream()
                .filter(merchantRaw -> !seenDomains.contains(merchantRaw.getDomain()))
                .peek(MerchantRaw::markInactive)
                .map(MerchantRaw::getDomain)
                .toList();
        if (!inactiveDomains.isEmpty()) {
            merchantRepository.findByDomainIn(inactiveDomains)
                    .forEach(merchant -> merchant.markInactive(fetchedAt));
        }
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
