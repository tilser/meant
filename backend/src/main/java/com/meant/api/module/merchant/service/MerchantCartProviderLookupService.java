package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.repository.MerchantCapabilityRepository;
import com.meant.api.module.merchant.repository.MerchantIntegrationRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationRouting;
import java.util.Optional;
import java.net.IDN;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantCartProviderLookupService {

    private final MerchantRepository merchantRepository;
    private final MerchantCapabilityRepository merchantCapabilityRepository;
    private final MerchantIntegrationRepository merchantIntegrationRepository;
    private final MerchantExecutionPolicyService merchantExecutionPolicyService;

    @Transactional(readOnly = true)
    public Optional<MerchantCartProvider> findById(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .map(this::toProvider);
    }

    @Transactional(readOnly = true)
    public Optional<MerchantCartProvider> findByDomain(String merchantDomain) {
        if (merchantDomain == null || merchantDomain.isBlank()) {
            return Optional.empty();
        }
        return merchantRepository.findByDomain(merchantDomain.trim())
                .map(this::toProvider);
    }

    @Transactional(readOnly = true)
    public Optional<MerchantCartProvider> findActiveByCanonicalDomain(String merchantDomain) {
        if (merchantDomain == null || merchantDomain.isBlank()) {
            return Optional.empty();
        }
        return merchantRepository.findByDomainAndActiveTrue(normalizeDomain(merchantDomain)).map(this::toProvider);
    }

    private String normalizeDomain(String domain) {
        String normalized = IDN.toASCII(domain.trim().toLowerCase(Locale.ROOT));
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.startsWith("www.") ? normalized.substring(4) : normalized;
    }

    private MerchantCartProvider toProvider(Merchant merchant) {
        var integrations = merchantIntegrationRepository.findByMerchantIdOrderByCreatedAtAsc(merchant.getId());
        Set<String> advertisedCapabilities = merchantCapabilityRepository.findNamesByMerchantId(merchant.getId());
        MerchantExecutionPolicy executionPolicy = merchantExecutionPolicyService.evaluate(
                merchant,
                integrations,
                advertisedCapabilities
        );
        return new MerchantCartProvider(
                merchant.getId(),
                merchant.getDomain(),
                merchant.getAdvertisedMcpEndpoint(),
                merchant.getProfileMcpEndpoint(),
                integrations.stream()
                        .map(integration -> new MerchantIntegrationRouting(
                                integration.getId(),
                                integration.getProvider(),
                                integration.getRoles(),
                                integration.getStatus(),
                                integration.getExternalMerchantId(),
                                integration.getVerifiedDomain(),
                                integration.getVerifiedShopIdentity(),
                                integration.getEndpoint()
                        ))
                        .toList(),
                executionPolicy,
                merchant.getProfileCapturedAt(),
                advertisedCapabilities
        );
    }
}
