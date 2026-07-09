package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.repository.MerchantCapabilityRepository;
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

    private static final String UCP_CHECKOUT_CAPABILITY = "dev.ucp.shopping.checkout";

    private final MerchantRepository merchantRepository;
    private final MerchantCapabilityRepository merchantCapabilityRepository;

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
                nativeCheckoutEnabled(merchant)
        );
    }

    /**
     * UCP checkout runs natively in the platform whenever the merchant advertises the checkout
     * capability in its /.well-known/ucp profile; the merchant flag stays as a manual override
     * for merchants whose profile has not been enriched yet.
     */
    private boolean nativeCheckoutEnabled(Merchant merchant) {
        return merchant.isNativeCheckoutEnabled()
                || merchantCapabilityRepository.existsByMerchantIdAndName(merchant.getId(), UCP_CHECKOUT_CAPABILITY);
    }
}
