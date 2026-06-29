package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.service.dto.CatalogLookupResult;
import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.query.GetMerchantProductDetailsQuery;
import com.meant.api.plugin.catalog.service.MerchantCatalogPluginDispatchService;
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
        MerchantSemanticSearchResult merchant = merchantLookupService.activeSearchResult(query.merchantId());
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
        return merchantCatalogPluginDispatchService.getProduct(
                merchant,
                lookupResult.productId(),
                context,
                activeCapabilities(lookupResult.negotiatedCapabilities(), NegotiatedCapabilities.none())
        );
    }

    private NegotiatedCapabilities activeCapabilities(
            NegotiatedCapabilities current,
            NegotiatedCapabilities fallback
    ) {
        return current == null || current.versions().isEmpty() ? fallback : current;
    }
}
