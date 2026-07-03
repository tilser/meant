package com.meant.api.module.review.controller;

import com.meant.api.module.review.controller.response.ProductReviewsResponse;
import com.meant.api.module.review.service.ReviewService;
import com.meant.api.module.review.service.query.GetProductReviewsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Product review provider-backed reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/merchants/{merchantId}/products")
    @Operation(
            summary = "Get product reviews",
            description = "Returns normalized product reviews from the discovered merchant review provider."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Product reviews",
            content = @Content(schema = @Schema(implementation = ProductReviewsResponse.class))
    )
    public ProductReviewsResponse getProductReviews(
            @Parameter(description = "Merchant UUID.", required = true)
            @PathVariable UUID merchantId,
            @Parameter(description = "Remote product id.", required = true)
            @RequestParam String productId,
            @Parameter(description = "Maximum reviews to return. Defaults to provider configuration when omitted.")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Review pagination offset.")
            @RequestParam(defaultValue = "0") Integer offset
    ) {
        return getProductReviewsResponse(merchantId, productId, limit, offset);
    }

    private ProductReviewsResponse getProductReviewsResponse(
            UUID merchantId,
            String productId,
            Integer limit,
            Integer offset
    ) {
        return ProductReviewsResponse.from(reviewService.getProductReviews(new GetProductReviewsQuery(
                merchantId,
                productId,
                limit,
                offset
        )));
    }

}
