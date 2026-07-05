package com.meant.api.module.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.properties.KlaviyoReviewProperties;
import com.meant.api.module.review.properties.OkendoReviewProperties;
import com.meant.api.module.review.properties.ReviewCacheProperties;
import com.meant.api.module.review.properties.YotpoReviewProperties;
import com.meant.api.module.review.service.ReviewProductIdNormalizer;
import com.meant.api.module.review.service.ReviewService;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import com.meant.api.module.review.service.query.GetProductReviewsQuery;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReviewControllerTest {

    private final CapturingReviewService reviewService = new CapturingReviewService();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewService)).build();

    @Test
    void getsProductReviewsWithRawShopifyGidQueryParameter() throws Exception {
        UUID merchantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        mockMvc.perform(get("/api/reviews/merchants/{merchantId}/products", merchantId)
                        .queryParam("productId", "gid://shopify/Product/123456")
                        .queryParam("limit", "5")
                        .queryParam("offset", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("gid://shopify/Product/123456"));

        assertThat(reviewService.query).isEqualTo(new GetProductReviewsQuery(
                merchantId,
                "gid://shopify/Product/123456",
                5,
                10
        ));
    }

    private static class CapturingReviewService extends ReviewService {

        private GetProductReviewsQuery query;

        private CapturingReviewService() {
            super(
                    null,
                    null,
                    null,
                    null,
                    new KlaviyoReviewProperties("https://reviews.example", 20),
                    new YotpoReviewProperties("https://yotpo.example", 20),
                    new OkendoReviewProperties("https://okendo.example", 20),
                    new ReviewCacheProperties(Duration.ofMinutes(1), 10L),
                    new ReviewProductIdNormalizer()
            );
        }

        @Override
        public ProductReviewsResult getProductReviews(GetProductReviewsQuery query) {
            this.query = query;
            return new ProductReviewsResult(
                    query.merchantId(),
                    query.productId(),
                    ReviewProviderType.KLAVIYO,
                    4.5,
                    1,
                    false,
                    List.of(),
                    false,
                    true,
                    null
            );
        }
    }
}
