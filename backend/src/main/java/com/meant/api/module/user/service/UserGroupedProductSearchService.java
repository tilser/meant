package com.meant.api.module.user.service;

import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.MerchantIntegrationLookupService;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByMerchantsQuery;
import com.meant.api.module.user.exception.UserProductSearchGroupingException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserProductSearchResult;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.catalog.common.service.ExactProductGroupingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserGroupedProductSearchService {

    private final UserProductSearchService userProductSearchService;
    private final MerchantIntegrationLookupService merchantIntegrationLookupService;
    private final UserCanonicalProductCandidateMapper candidateMapper;
    private final ExactProductGroupingService exactProductGroupingService;

    public UserGroupedProductSearchResult search(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SearchUserProductsCommand command
    ) {
        UserProductSearchResult flatResult = userProductSearchService.search(profileCommand, command);
        Instant observedAt = Instant.now();
        Map<UUID, List<MerchantIntegrationResult>> integrations = integrations(flatResult.products());
        List<ProductCandidate> candidates = flatResult.products().stream()
                .map(product -> candidateMapper.from(
                        product,
                        integration(product, integrations.getOrDefault(product.merchantId(), List.of())),
                        observedAt,
                        flatResult.cached()
                                ? ResultSourceType.CACHED_OBSERVATION
                                : ResultSourceType.MERCHANT_STOREFRONT
                ))
                .toList();
        return new UserGroupedProductSearchResult(
                flatResult.query(),
                flatResult.normalizedQuery(),
                flatResult.profileHash(),
                flatResult.cached(),
                flatResult.offset(),
                flatResult.limit(),
                flatResult.nextOffset(),
                flatResult.hasMore(),
                exactProductGroupingService.group(candidates)
        );
    }

    private Map<UUID, List<MerchantIntegrationResult>> integrations(
            List<UserProductSearchProductResult> products
    ) {
        Set<UUID> merchantIds = products.stream()
                .map(UserProductSearchProductResult::merchantId)
                .collect(Collectors.toUnmodifiableSet());
        if (merchantIds.isEmpty()) {
            return Map.of();
        }
        return merchantIntegrationLookupService.listByMerchants(
                        new ListMerchantIntegrationsByMerchantsQuery(merchantIds)
                ).stream()
                .filter(integration -> integration.status() == MerchantIntegrationStatus.ACTIVE)
                .filter(integration -> integration.roles().contains(MerchantIntegrationRole.STOREFRONT_CATALOG)
                        || integration.roles().contains(MerchantIntegrationRole.CATALOG_PROVENANCE))
                .sorted(Comparator.comparing(MerchantIntegrationResult::id))
                .collect(Collectors.groupingBy(
                        MerchantIntegrationResult::merchantId,
                        LinkedHashMap::new,
                        Collectors.toUnmodifiableList()
                ));
    }

    private MerchantIntegrationResult integration(
            UserProductSearchProductResult product,
            List<MerchantIntegrationResult> integrations
    ) {
        List<MerchantIntegrationResult> endpointMatches = integrations.stream()
                .filter(integration -> Objects.equals(integration.endpoint(), product.endpoint()))
                .toList();
        if (endpointMatches.size() == 1) {
            return endpointMatches.getFirst();
        }
        if (endpointMatches.isEmpty() && integrations.size() == 1) {
            return integrations.getFirst();
        }
        throw new UserProductSearchGroupingException(
                "Could not resolve one catalog integration for merchant %s and endpoint %s"
                        .formatted(product.merchantId(), product.endpoint())
        );
    }
}
