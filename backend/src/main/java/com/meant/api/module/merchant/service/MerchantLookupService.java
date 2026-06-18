package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.exception.MerchantCatalogSearchException;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantLookupService {

    private final MerchantRepository merchantRepository;

    public MerchantSemanticSearchResult activeSearchResult(UUID merchantId) {
        Merchant merchant = merchantRepository.findByIdAndActiveTrue(merchantId)
                .orElseThrow(() -> MerchantCatalogSearchException.notFound(
                        "Active merchant not found: " + merchantId
                ));
        return new MerchantSemanticSearchResult(
                merchant.getId(),
                merchant.getDomain(),
                merchant.getName(),
                merchant.getAdvertisedMcpEndpoint(),
                merchant.getProfileMcpEndpoint(),
                merchant.getDescription(),
                1.0d,
                1.0d,
                1
        );
    }
}
