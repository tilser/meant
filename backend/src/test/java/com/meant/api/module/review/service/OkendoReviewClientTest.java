package com.meant.api.module.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.properties.OkendoReviewProperties;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class OkendoReviewClientTest {

    @Test
    void fetchReviewsNormalizesOkendoReviewAndAggregateResponses() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        OkendoReviewClient client = new OkendoReviewClient(
                restClientBuilder.build(),
                new OkendoReviewProperties("https://api.okendo.io/v1", 20),
                new OkendoReviewResponseMapper(new ObjectMapper())
        );
        UUID merchantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/v1/stores/ac3ddecd-d40f-41bb-8e17-3a68e331cc08"
                                    + "/products/shopify-15265473495425/reviews");
                    assertThat(request.getURI().getQuery())
                            .contains("limit=4");
                    assertThat(request.getURI().getRawQuery())
                            .contains("orderBy=date%20desc");
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(reviewsFixture(), MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/v1/stores/ac3ddecd-d40f-41bb-8e17-3a68e331cc08"
                                + "/products/shopify-15265473495425/review_aggregate"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(aggregateFixture(), MediaType.APPLICATION_JSON));

        ProductReviewsResult result = client.fetchReviews(
                merchantId,
                "15265473495425",
                "ac3ddecd-d40f-41bb-8e17-3a68e331cc08",
                2,
                2
        );

        assertThat(result.merchantId()).isEqualTo(merchantId);
        assertThat(result.productId()).isEqualTo("15265473495425");
        assertThat(result.provider()).isEqualTo(ReviewProviderType.OKENDO);
        assertThat(result.rating()).isEqualTo(4.7155172413793105d);
        assertThat(result.reviewCount()).isEqualTo(116);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.cached()).isFalse();
        assertThat(result.supported()).isTrue();
        assertThat(result.reviews()).hasSize(2);
        assertThat(result.reviews().getFirst().externalId()).isEqualTo("review-3");
        assertThat(result.reviews().getFirst().author()).isEqualTo("Grace H.");
        assertThat(result.reviews().getFirst().rating()).isEqualTo(4);
        assertThat(result.reviews().getFirst().content()).isEqualTo("Offset review.");
        assertThat(result.reviews().getFirst().verified()).isFalse();
        assertThat(result.reviews().getFirst().createdAt()).hasToString("2026-07-02T12:00:00Z");
        assertThat(result.reviews().getFirst().variantId()).isEqualTo("55080553644419");
        assertThat(result.reviews().getFirst().variantTitle()).isEqualTo("3 Tubes");
        server.verify();
    }

    private String reviewsFixture() {
        return """
                {
                  "nextUrl": "/stores/ac3ddecd-d40f-41bb-8e17-3a68e331cc08/products/shopify-15265473495425/reviews?lastEvaluated=abc",
                  "reviews": [
                    {
                      "reviewId": "review-1",
                      "body": "Newest review.",
                      "dateCreated": "2026-07-04T13:25:44.422Z",
                      "rating": 5,
                      "reviewer": {
                        "displayName": "Ada L.",
                        "isVerified": true
                      },
                      "variantId": "55080553644417",
                      "productVariantName": "1 Tube"
                    },
                    {
                      "reviewId": "review-2",
                      "body": "Second review.",
                      "dateCreated": "2026-07-03T10:15:00.000Z",
                      "rating": 5,
                      "reviewer": {
                        "displayName": "Charles M.",
                        "isVerified": true
                      },
                      "variantId": "55080553644418",
                      "productVariantName": "2 Tubes"
                    },
                    {
                      "reviewId": "review-3",
                      "body": "Offset review.",
                      "dateCreated": "2026-07-02T12:00:00.000Z",
                      "rating": 4,
                      "reviewer": {
                        "displayName": "Grace H.",
                        "isVerified": false
                      },
                      "variantId": "55080553644419",
                      "productVariantName": "3 Tubes"
                    },
                    {
                      "reviewId": "review-4",
                      "body": "Older review.",
                      "dateCreated": "2026-07-01T12:00:00.000Z",
                      "rating": 5,
                      "reviewer": {
                        "displayName": "Lin T.",
                        "isVerified": true
                      }
                    }
                  ]
                }
                """;
    }

    private String aggregateFixture() {
        return """
                {
                  "reviewAggregate": {
                    "ratingAndReviewCount": 116,
                    "ratingAndReviewValuesTotal": 547,
                    "reviewCount": 116
                  }
                }
                """;
    }
}
