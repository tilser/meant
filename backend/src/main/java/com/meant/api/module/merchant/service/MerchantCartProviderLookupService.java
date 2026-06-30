package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantCartProviderLookupService {

    private final MerchantRepository merchantRepository;

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

    private MerchantCartProvider toProvider(Merchant merchant) {
        return new MerchantCartProvider(
                merchant.getId(),
                merchant.getDomain(),
                merchant.getAdvertisedMcpEndpoint(),
                merchant.getProfileMcpEndpoint(),
                merchant.isNativeCheckoutEnabled()
        );
    }
}
