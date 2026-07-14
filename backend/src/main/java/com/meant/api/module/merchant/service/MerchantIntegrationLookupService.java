package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.MerchantIntegration;
import com.meant.api.module.merchant.repository.MerchantIntegrationRepository;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.query.GetMerchantIntegrationByProviderIdentityQuery;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByMerchantsQuery;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByIdsQuery;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByVerifiedDomainQuery;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class MerchantIntegrationLookupService {

    private final MerchantIntegrationRepository merchantIntegrationRepository;

    @Transactional(readOnly = true)
    public List<MerchantIntegrationResult> listByMerchant(
            @NotNull @Valid ListMerchantIntegrationsQuery query
    ) {
        return merchantIntegrationRepository.findByMerchantIdOrderByCreatedAtAsc(query.merchantId()).stream()
                .map(this::toResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MerchantIntegrationResult> listByMerchants(
            @NotNull @Valid ListMerchantIntegrationsByMerchantsQuery query
    ) {
        return merchantIntegrationRepository.findByMerchantIdInOrderByCreatedAtAsc(query.merchantIds()).stream()
                .map(this::toResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MerchantIntegrationResult> listByIds(
            @NotNull @Valid ListMerchantIntegrationsByIdsQuery query
    ) {
        return merchantIntegrationRepository.findByIdInOrderByCreatedAtAsc(query.integrationIds()).stream()
                .map(this::toResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<MerchantIntegrationResult> findByProviderIdentity(
            @NotNull @Valid GetMerchantIntegrationByProviderIdentityQuery query
    ) {
        return merchantIntegrationRepository.findByProviderAndExternalMerchantId(
                        query.provider(),
                        query.externalMerchantId().trim()
                )
                .map(this::toResult);
    }

    @Transactional(readOnly = true)
    public List<MerchantIntegrationResult> listByVerifiedDomain(
            @NotNull @Valid ListMerchantIntegrationsByVerifiedDomainQuery query
    ) {
        return merchantIntegrationRepository.findByNormalizedVerifiedDomainOrderByCreatedAtAsc(
                        normalizeDomain(query.verifiedDomain())
                ).stream()
                .map(this::toResult)
                .toList();
    }

    private String normalizeDomain(String domain) {
        // This validated query input has a stricter contract than nullable persisted identities.
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private MerchantIntegrationResult toResult(MerchantIntegration integration) {
        return new MerchantIntegrationResult(
                integration.getId(),
                integration.getMerchant().getId(),
                integration.getMerchant().getName(),
                integration.getProvider(),
                integration.getKind(),
                integration.getRoles(),
                integration.getExternalMerchantId(),
                integration.getVerifiedDomain(),
                integration.getVerifiedShopIdentity(),
                integration.getEndpoint(),
                integration.getProtocolVersion(),
                integration.getAuthStrategy(),
                integration.getStatus(),
                integration.getSource(),
                integration.getCapturedAt(),
                integration.getCreatedAt(),
                integration.getUpdatedAt()
        );
    }
}
