package com.meant.api.module.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.review.properties.KlaviyoReviewProperties;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class KlaviyoReviewClientTest {

    @Test
    void fetchReviewsNormalizesKlaviyoReviewResponse() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        KlaviyoReviewClient client = new KlaviyoReviewClient(
                restClientBuilder.build(),
                new KlaviyoReviewProperties("https://fast.a.klaviyo.com/reviews/api", 20),
                new KlaviyoReviewResponseMapper(new ObjectMapper())
        );
        UUID merchantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/reviews/api/client_reviews/8802707341562/");
                    assertThat(request.getURI().getQuery())
                            .contains("product_id=8802707341562")
                            .contains("company_id=J5feSG")
                            .contains("limit=5")
                            .contains("offset=0")
                            .contains("sort=3")
                            .contains("filter=")
                            .contains("type=reviews")
                            .contains("media=false");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        ProductReviewsResult result = client.fetchReviews(
                merchantId,
                "8802707341562",
                "J5feSG",
                5,
                0
        );

        assertThat(result.merchantId()).isEqualTo(merchantId);
        assertThat(result.productId()).isEqualTo("8802707341562");
        assertThat(result.rating()).isEqualTo(4.7d);
        assertThat(result.reviewCount()).isEqualTo(123);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.cached()).isFalse();
        assertThat(result.reviews()).hasSize(3);
        assertThat(result.reviews().getFirst().author()).isEqualTo("Jane Doe");
        assertThat(result.reviews().getFirst().rating()).isEqualTo(5);
        assertThat(result.reviews().getFirst().content()).isEqualTo("Great fit.");
        assertThat(result.reviews().getFirst().verified()).isTrue();
        assertThat(result.reviews().getFirst().createdAt()).hasToString("2026-01-02T03:04:05Z");
        assertThat(result.reviews().getFirst().variantId()).isEqualTo("47424342196474");
        assertThat(result.reviews().getFirst().variantTitle()).isEqualTo("White / XS");
        assertThat(result.reviews().get(1).content()).isNull();
        assertThat(result.reviews().get(1).rating()).isEqualTo(4);
        assertThat(result.reviews().get(2).author()).isEqualTo("Root Fallback");
        server.verify();
    }

    private String fixture() {
        return """
                {
                  "summary": {
                    "rating": 4.7,
                    "review_count": 123
                  },
                  "has_more": true,
                  "filtered_count": 123,
                  "reviews": [
                    {
                      "id": "review-1",
                      "author": "Jane Doe",
                      "rating": 5,
                      "content": "Great fit.",
                      "verified": true,
                      "created_at": "2026-01-02T03:04:05Z",
                      "variant_id": "47424342196474",
                      "variant_title": "White / XS"
                    },
                    {
                      "id": "review-2",
                      "author": "Rating Only",
                      "rating": 4,
                      "content": null,
                      "verified_buyer": false,
                      "created_at": "2026-01-03"
                    },
                    {
                      "id": "review-3",
                      "author": {
                        "email": "fallback@example.com"
                      },
                      "customer_name": "Root Fallback",
                      "rating": 5,
                      "content": "Fallback author.",
                      "verified": 1,
                      "created_at": "2026-01-04"
                    }
                  ],
                  "product": {
                    "id": "8802707341562"
                  }
                }
                """;
    }
}
