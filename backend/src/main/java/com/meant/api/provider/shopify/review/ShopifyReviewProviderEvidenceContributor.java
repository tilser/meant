package com.meant.api.provider.shopify.review;

import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.service.port.ReviewProviderEvidenceContributor;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ShopifyReviewProviderEvidenceContributor implements ReviewProviderEvidenceContributor {

    private static final Map<ReviewProviderType, List<String>> APP_EVIDENCE = Map.of(
            ReviewProviderType.OKENDO, List.of("shopify://apps/okendo"),
            ReviewProviderType.JUDGE_ME, List.of("shopify://apps/judge-me-reviews"),
            ReviewProviderType.JUNIP, List.of("shopify://apps/junip")
    );

    @Override
    public List<String> evidencePatterns(ReviewProviderType provider) {
        return APP_EVIDENCE.getOrDefault(provider, List.of());
    }
}
