package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.service.dto.CatalogLookupResult;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.MerchantProductDetailsLookupContext;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.query.GetMerchantProductDetailsQuery;
import com.meant.api.module.merchant.service.MerchantCatalogPluginDispatchService;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class MerchantProductDetailsService {

    private final MerchantLookupService merchantLookupService;
    private final MerchantCatalogPluginDispatchService merchantCatalogPluginDispatchService;

    public ProductDetailsResult get(@NotNull @Valid GetMerchantProductDetailsQuery query) {
        MerchantProductDetailsLookupContext merchantContext =
                merchantLookupService.activeProductDetailsContext(query.merchantId());
        MerchantSemanticSearchResult merchant = merchantContext.routingMerchant();
        CatalogSearchContext context = new CatalogSearchContext(
                query.addressCountry(),
                null,
                null,
                query.language(),
                null,
                "Product detail"
        );
        CatalogLookupResult lookupResult = merchantCatalogPluginDispatchService.lookupCatalog(
                merchant,
                query.productId(),
                context,
                NegotiatedCapabilities.none()
        );
        NegotiatedCapabilities capabilities = activeCapabilities(
                lookupResult.negotiatedCapabilities(), NegotiatedCapabilities.none());
        ProductDetailsResult details = query.selectionRequest()
                ? merchantCatalogPluginDispatchService.getProduct(
                        merchant,
                        lookupResult.productId(),
                        query.selectedOptions().stream()
                                .map(option -> new ProductDetailsResponse.SelectedOption(option.name(), option.value()))
                                .toList(),
                        query.preferences(),
                        context,
                        capabilities)
                : merchantCatalogPluginDispatchService.getProduct(
                        merchant,
                        lookupResult.productId(),
                        context,
                        capabilities);
        return details.withBuyerContext(
                merchant.domain(),
                merchantContext.technicalEndpointAliases()
        );
    }

    private NegotiatedCapabilities activeCapabilities(
            NegotiatedCapabilities current,
            NegotiatedCapabilities fallback
    ) {
        return current == null || current.versions().isEmpty() ? fallback : current;
    }
}
