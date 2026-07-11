package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.MerchantOrderSourceResult;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantOrderSourceLookupService {

    private final MerchantRepository merchantRepository;

    @Transactional(readOnly = true)
    public Optional<MerchantOrderSourceResult> findByDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return Optional.empty();
        }
        return merchantRepository.findByDomain(domain.trim())
                .map(this::toResult);
    }

    private MerchantOrderSourceResult toResult(Merchant merchant) {
        return new MerchantOrderSourceResult(merchant.getId(), merchant.getDomain(), merchant.getName());
    }
}
