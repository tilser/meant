package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.service.dto.AgentProductReadSelection;
import com.meant.api.module.agent.service.dto.AgentProductReviewsResult;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.GetProductReviewsAgentToolInput;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.service.ReviewService;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import com.meant.api.module.review.service.query.GetProductReviewsQuery;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GetProductReviewsAgentToolTest {

    @Test
    void loadsLiveReviewsWhenTheOfferHasALocalMerchantIntegration() {
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentProductReadSelectionService selectionService = mock(AgentProductReadSelectionService.class);
        ReviewService reviewService = mock(ReviewService.class);
        AgentProductReadSelection selection = selection("product-1", "offer-1", "external-product-1");
        MerchantIntegrationResult integration = mock(MerchantIntegrationResult.class);
        UUID merchantId = UUID.randomUUID();
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), "Show reviews");
        ProductReviewsResult liveReviews = new ProductReviewsResult(
                merchantId,
                "external-product-1",
                ReviewProviderType.OKENDO,
                4.8,
                12,
                false,
                List.of(),
                false,
                true,
                null
        );

        when(json.readArguments("{}", GetProductReviewsAgentToolInput.class))
                .thenReturn(new GetProductReviewsAgentToolInput("product-1", "offer-1", 5, 0));
        when(selectionService.select(context, "product-1", "offer-1", false)).thenReturn(selection);
        when(selection.merchantIntegration()).thenReturn(integration);
        when(integration.merchantId()).thenReturn(merchantId);
        when(reviewService.getProductReviews(new GetProductReviewsQuery(
                merchantId, "external-product-1", 5, 0))).thenReturn(liveReviews);
        when(json.writeArtifact(any())).thenReturn("{\"supported\":true}");
        when(json.write(any())).thenReturn("{\"supported\":true}");

        var result = new GetProductReviewsAgentTool(json, selectionService, reviewService)
                .execute(context, "{}");

        ArgumentCaptor<AgentProductReviewsResult> payload =
                ArgumentCaptor.forClass(AgentProductReviewsResult.class);
        verify(json).writeArtifact(payload.capture());
        assertThat(payload.getValue()).satisfies(reviews -> {
            assertThat(reviews.supported()).isTrue();
            assertThat(reviews.merchantId()).isEqualTo(merchantId.toString());
            assertThat(reviews.provider()).isEqualTo(ReviewProviderType.OKENDO);
            assertThat(reviews.rating()).isEqualTo(4.8);
            assertThat(reviews.reviewCount()).isEqualTo(12);
        });
        assertThat(result.safeSummary()).isEqualTo("Loaded 0 product review(s).");
    }

    @Test
    void returnsAnUnsupportedReviewArtifactWhenTheOfferHasNoLocalMerchantIntegration() {
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentProductReadSelectionService selectionService = mock(AgentProductReadSelectionService.class);
        ReviewService reviewService = mock(ReviewService.class);
        AgentProductReadSelection selection = selection("product-1", "offer-1", "external-product-1");
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), "Show reviews");

        when(json.readArguments("{}", GetProductReviewsAgentToolInput.class))
                .thenReturn(new GetProductReviewsAgentToolInput("product-1", "offer-1", null, null));
        when(selectionService.select(context, "product-1", "offer-1", false)).thenReturn(selection);
        when(json.writeArtifact(any())).thenReturn("{\"supported\":false}");
        when(json.write(any())).thenReturn("{\"supported\":false}");

        var result = new GetProductReviewsAgentTool(json, selectionService, reviewService)
                .execute(context, "{}");

        ArgumentCaptor<AgentProductReviewsResult> payload =
                ArgumentCaptor.forClass(AgentProductReviewsResult.class);
        verify(json).writeArtifact(payload.capture());
        verifyNoInteractions(reviewService);
        assertThat(payload.getValue()).satisfies(reviews -> {
            assertThat(reviews.supported()).isFalse();
            assertThat(reviews.merchantId()).isNull();
            assertThat(reviews.productId()).isEqualTo("external-product-1");
            assertThat(reviews.reviews()).isEmpty();
            assertThat(reviews.message()).contains("does not have a connected review provider");
        });
        assertThat(result.safeSummary()).contains("does not have a connected review provider");
        assertThat(result.artifacts()).singleElement().satisfies(artifact -> {
            assertThat(artifact.type()).isEqualTo(AgentArtifactType.REVIEWS);
            assertThat(artifact.canonicalProductKey()).isEqualTo("product-1");
            assertThat(artifact.offerKey()).isEqualTo("offer-1");
        });
    }

    private AgentProductReadSelection selection(String productKey, String offerKey, String externalProductId) {
        AgentProductReadSelection selection = mock(AgentProductReadSelection.class);
        UserProductDetailResult detail = mock(UserProductDetailResult.class);
        CanonicalProduct product = mock(CanonicalProduct.class);
        Offer offer = mock(Offer.class);
        OfferIdentity identity = mock(OfferIdentity.class);
        ExternalIdentifier productIdentity = mock(ExternalIdentifier.class);
        when(selection.detail()).thenReturn(detail);
        when(selection.offer()).thenReturn(offer);
        when(selection.merchantIntegration()).thenReturn(null);
        when(detail.product()).thenReturn(product);
        when(product.key()).thenReturn(productKey);
        when(product.title()).thenReturn("Test jacket");
        when(offer.key()).thenReturn(offerKey);
        when(offer.identity()).thenReturn(identity);
        when(identity.externalProductIdentity()).thenReturn(productIdentity);
        when(productIdentity.value()).thenReturn(externalProductId);
        return selection;
    }
}
