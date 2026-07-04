package com.meant.api.module.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.properties.YotpoReviewProperties;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class YotpoReviewClientTest {

    @Test
    void fetchReviewsNormalizesYotpoReviewResponse() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        YotpoReviewClient client = new YotpoReviewClient(
                restClientBuilder.build(),
                new YotpoReviewProperties("https://api-cdn.yotpo.com/v1/widget", 20),
                new YotpoReviewResponseMapper(new ObjectMapper())
        );
        UUID merchantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/v1/widget/store-key/products/7365959123057/reviews.json");
                    assertThat(request.getURI().getQuery())
                            .contains("per_page=5")
                            .contains("page=2");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        ProductReviewsResult result = client.fetchReviews(
                merchantId,
                "7365959123057",
                "store-key",
                5,
                5
        );

        assertThat(result.merchantId()).isEqualTo(merchantId);
        assertThat(result.productId()).isEqualTo("7365959123057");
        assertThat(result.provider()).isEqualTo(ReviewProviderType.YOTPO);
        assertThat(result.rating()).isEqualTo(5.0d);
        assertThat(result.reviewCount()).isEqualTo(6);
        assertThat(result.hasMore()).isFalse();
        assertThat(result.cached()).isFalse();
        assertThat(result.supported()).isTrue();
        assertThat(result.reviews()).hasSize(1);
        assertThat(result.reviews().getFirst().externalId()).isEqualTo("846862660");
        assertThat(result.reviews().getFirst().author()).isEqualTo("Ada L.");
        assertThat(result.reviews().getFirst().rating()).isEqualTo(5);
        assertThat(result.reviews().getFirst().content()).isEqualTo("Excellent quality.");
        assertThat(result.reviews().getFirst().verified()).isTrue();
        assertThat(result.reviews().getFirst().createdAt()).hasToString("2026-06-03T02:49:00Z");
        server.verify();
    }

    private String fixture() {
        return """
                {
                  "status": {
                    "code": 200,
                    "message": "OK"
                  },
                  "response": {
                    "pagination": {
                      "page": 2,
                      "per_page": 5,
                      "total": 6
                    },
                    "bottomline": {
                      "total_review": 6,
                      "average_score": 5.0
                    },
                    "reviews": [
                      {
                        "id": 846862660,
                        "score": 5,
                        "content": "Excellent quality.",
                        "created_at": "2026-06-03T02:49:00.000Z",
                        "verified_buyer": true,
                        "user": {
                          "display_name": "Ada L."
                        }
                      }
                    ]
                  }
                }
                """;
    }
}
