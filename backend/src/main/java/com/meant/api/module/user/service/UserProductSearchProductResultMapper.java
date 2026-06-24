package com.meant.api.module.user.service;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.user.entity.UserProductSearchResultItem;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult.RichCatalogData;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserProductSearchProductResultMapper {

    public UserProductSearchProductResult from(
            UserProductSearchProductSnapshot product,
            UserProductRecommendationExplanationResult explanation,
            Instant now
    ) {
        return UserProductSearchProductResult.from(
                UserProductSearchResultItem.from(
                        UUID.randomUUID(),
                        product.productKey(),
                        product.productHash(),
                        product.product(),
                        now
                ),
                explanation,
                richCatalogData(product.product())
        );
    }

    private RichCatalogData richCatalogData(MerchantSemanticProductResult product) {
        return new RichCatalogData(
                safeList(product.media()),
                safeList(product.categories()),
                safeList(product.certifications()),
                safeList(product.materials()),
                safeList(product.skus()),
                safeList(product.collections()),
                safeList(product.attributes())
        );
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
