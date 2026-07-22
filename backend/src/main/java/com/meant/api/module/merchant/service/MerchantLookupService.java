package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantIntegration;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.exception.MerchantCatalogSearchException;
import com.meant.api.module.merchant.repository.MerchantIntegrationRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantLookupService {

    private final MerchantRepository merchantRepository;
    private final MerchantIntegrationRepository merchantIntegrationRepository;

    public MerchantSemanticSearchResult activeSearchResult(UUID merchantId) {
        Merchant merchant = merchantRepository.findByIdAndActiveTrue(merchantId)
                .orElseThrow(() -> MerchantCatalogSearchException.notFound(
                        "Active merchant not found: " + merchantId
                ));
        String endpoint = catalogEndpoint(merchant);
        return new MerchantSemanticSearchResult(
                merchant.getId(),
                merchant.getDomain(),
                merchant.getName(),
                endpoint,
                null,
                merchant.getDescription(),
                1.0d,
                1.0d,
                1
        );
    }

    private String catalogEndpoint(Merchant merchant) {
        java.util.List<MerchantIntegration> integrations = merchantIntegrationRepository
                .findByMerchantIdAndProviderAndStatusAndRole(
                        merchant.getId(),
                        MerchantIntegrationProvider.GENERIC_UCP,
                        MerchantIntegrationStatus.ACTIVE,
                        MerchantIntegrationRole.STOREFRONT_CATALOG
                );
        if (integrations.size() != 1) {
            throw new MerchantCatalogSearchException(
                    "Merchant must have exactly one active storefront catalog endpoint: " + merchant.getId()
            );
        }
        return integrations.getFirst().getEndpoint();
    }
}
