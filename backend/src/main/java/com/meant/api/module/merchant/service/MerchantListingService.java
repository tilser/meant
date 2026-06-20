package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.MerchantListItemResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantListingService {

    private final MerchantRepository merchantRepository;

    public List<MerchantListItemResult> listActiveMerchants() {
        return merchantRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(MerchantListingService::toResult)
                .toList();
    }

    private static MerchantListItemResult toResult(Merchant merchant) {
        return new MerchantListItemResult(
                merchant.getId(),
                merchant.getDomain(),
                merchant.getName(),
                merchant.getDescription(),
                merchant.getAdvertisedMcpEndpoint(),
                merchant.getProfileMcpEndpoint(),
                merchant.getMerchantRaw().isHasIdentityLinking()
        );
    }
}
