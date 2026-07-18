package com.meant.api.module.user.controller.response;

import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.module.user.service.dto.UserOfferCommercialState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Transient grouped results for a query-narrowed similarity search. */
@Schema(description = "Version 1 transient similar-product response with canonical products and merchant offers")
public record UserSimilarProductSearchV1Response(
        @Schema(description = "Originating user search query", requiredMode = Schema.RequiredMode.REQUIRED)
        String query,
        @Schema(description = "Normalized cache identity for the search", requiredMode = Schema.RequiredMode.REQUIRED)
        String normalizedQuery,
        @Schema(description = "Taste and settings profile hash used for the search", requiredMode = Schema.RequiredMode.REQUIRED)
        String profileHash,
        @Schema(description = "Whether results came from a source-approved cache", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean cached,
        @Schema(description = "Canonical-product offset applied after grouping", requiredMode = Schema.RequiredMode.REQUIRED)
        int offset,
        @Schema(description = "Canonical-product page size applied after grouping", requiredMode = Schema.RequiredMode.REQUIRED)
        int limit,
        @Schema(description = "Always absent because similarity search has no continuation", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer nextOffset,
        @Schema(description = "Always false because similarity search returns one fixed page", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasMore,
        @Schema(
                description = "Whether an upstream source reported more candidates than this live request could materialize",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean upstreamTruncated,
        @Schema(description = "Typed completion, degradation, and truncation state for every invoked source", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserCatalogSourceStateResponse> sourceStates,
        @Schema(description = "Deterministically ordered canonical products", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserGroupedProductSearchV1Response.CanonicalProductResponse> products,
        @Schema(description = "Total typed reconciliation decisions in the fetched candidate window", requiredMode = Schema.RequiredMode.REQUIRED)
        int groupingDecisionCount,
        @Schema(description = "Whether grouping decisions were omitted by page filtering or the public diagnostic bound", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean groupingDecisionsTruncated,
        @Schema(description = "Typed exact-match and conservative non-match decisions for this page", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserGroupedProductSearchV1Response.ProductGroupingDecisionResponse> groupingDecisions
) {

    public static UserSimilarProductSearchV1Response from(UserGroupedProductSearchResult result) {
        return new UserSimilarProductSearchV1Response(
                result.query(),
                result.normalizedQuery(),
                result.profileHash(),
                result.cached(),
                result.offset(),
                result.limit(),
                result.nextOffset(),
                result.hasMore(),
                result.upstreamTruncated(),
                result.sourceStates().stream().map(UserCatalogSourceStateResponse::from).toList(),
                result.products().stream()
                        .map(product -> UserGroupedProductSearchV1Response.CanonicalProductResponse.from(
                                product,
                                result.productRankingExplanations().get(product.key()),
                                result.productPersonalizations().get(product.key()),
                                result.offerRankingExplanations(),
                                product.offers().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                                        Offer::key, UserOfferCommercialState::discovery))
                        ))
                        .toList(),
                result.groupingDecisionCount(),
                result.groupingDecisionsTruncated(),
                result.groupingDecisions().stream()
                        .map(UserGroupedProductSearchV1Response.ProductGroupingDecisionResponse::from)
                        .toList()
        );
    }
}
