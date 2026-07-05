package com.meant.api.module.merchant.service.dto;

import java.util.List;

public record ProductRichCatalogData(
        Long listPriceAmount,
        String listPriceCurrency,
        Double ratingScore,
        Integer reviewCount,
        List<ProductCatalogMedia> media,
        List<ProductCatalogCategory> categories,
        List<String> certifications,
        List<String> materials,
        List<String> skus,
        List<String> collections,
        List<ProductCatalogAttribute> attributes
) {
}
