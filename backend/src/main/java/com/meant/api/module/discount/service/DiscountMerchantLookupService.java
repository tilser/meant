package com.meant.api.module.discount.service;

import com.meant.api.module.discount.exception.DiscountCodeException;
import com.meant.api.module.discount.service.dto.DiscountMerchant;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.repository.MerchantRepository;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiscountMerchantLookupService {

    private final MerchantRepository merchantRepository;

    @Transactional(readOnly = true)
    public DiscountMerchant find(UUID merchantId, String merchantDomain) {
        if (merchantId != null) {
            return merchantRepository.findById(merchantId)
                    .map(this::toResult)
                    .orElseThrow(() -> DiscountCodeException.notFound("Merchant not found: " + merchantId));
        }
        String normalizedDomain = normalizeDomain(merchantDomain);
        if (normalizedDomain != null) {
            Set<String> candidates = domainCandidates(normalizedDomain);
            Map<String, Merchant> merchantsByDomain = merchantRepository.findByDomainIn(candidates).stream()
                    .collect(Collectors.toMap(Merchant::getDomain, Function.identity()));
            return candidates.stream()
                    .map(merchantsByDomain::get)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .map(this::toResult)
                    .orElseThrow(() -> DiscountCodeException.notFound("Merchant not found: " + normalizedDomain));
        }
        throw new DiscountCodeException("merchantId or merchantDomain is required");
    }

    private DiscountMerchant toResult(Merchant merchant) {
        return new DiscountMerchant(
                merchant.getId(),
                merchant.getDomain(),
                merchant.getName(),
                merchant.getAdvertisedMcpEndpoint(),
                merchant.getProfileMcpEndpoint(),
                merchant.isNativeCheckoutEnabled()
        );
    }

    private String normalizeDomain(String merchantDomain) {
        if (merchantDomain == null || merchantDomain.isBlank()) {
            return null;
        }
        String normalized = merchantDomain.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("https://")) {
            normalized = normalized.substring("https://".length());
        } else if (normalized.startsWith("http://")) {
            normalized = normalized.substring("http://".length());
        }
        int slashIndex = normalized.indexOf('/');
        return slashIndex < 0 ? normalized : normalized.substring(0, slashIndex);
    }

    private Set<String> domainCandidates(String normalizedDomain) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(normalizedDomain);
        if (normalizedDomain.startsWith("www.")) {
            candidates.add(normalizedDomain.substring("www.".length()));
        } else {
            candidates.add("www." + normalizedDomain);
        }
        return candidates;
    }
}
