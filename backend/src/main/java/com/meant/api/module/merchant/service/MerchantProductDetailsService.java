package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.query.GetMerchantProductDetailsQuery;
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
    private final MerchantProductDetailsClient merchantProductDetailsClient;

    public ProductDetailsResult get(@NotNull @Valid GetMerchantProductDetailsQuery query) {
        MerchantSemanticSearchResult merchant = merchantLookupService.activeSearchResult(query.merchantId());
        return merchantProductDetailsClient.getProductDetails(
                merchant,
                query.productId(),
                new CatalogSearchContext(query.addressCountry(), null, null, query.language(), null, "Product detail")
        );
    }
}
