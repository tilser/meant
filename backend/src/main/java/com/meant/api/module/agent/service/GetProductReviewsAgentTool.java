package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentProductReadSelection;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.GetProductReviewsAgentToolInput;
import com.meant.api.module.review.service.ReviewService;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import com.meant.api.module.review.service.query.GetProductReviewsQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetProductReviewsAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "get_product_reviews",
            "Load real merchant review-provider results for a previously shown exact product offer.",
            """
            {"type":"object","properties":{"canonicalProductKey":{"type":"string","minLength":1,"maxLength":200},"selectedOfferKey":{"type":"string","minLength":1,"maxLength":200},"limit":{"type":"integer","minimum":1,"maximum":20},"offset":{"type":"integer","minimum":0,"maximum":1000}},"required":["canonicalProductKey"],"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentProductReadSelectionService selectionService;
    private final ReviewService reviewService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        GetProductReviewsAgentToolInput input = json.readArguments(
                argumentsJson, GetProductReviewsAgentToolInput.class);
        int limit = input.limit() == null ? 10 : input.limit();
        int offset = input.offset() == null ? 0 : input.offset();
        if (limit < 1 || limit > 20 || offset < 0 || offset > 1000) {
            throw AgentProductReadToolException.invalid("Review limit or offset is outside the allowed range.");
        }
        AgentProductReadSelection selection = selectionService.select(
                context, input.canonicalProductKey(), input.selectedOfferKey(), true);
        ProductReviewsResult reviews = reviewService.getProductReviews(new GetProductReviewsQuery(
                selection.merchantIntegration().merchantId(),
                selection.offer().identity().externalProductIdentity().value(),
                limit,
                offset
        ));
        AgentArtifact artifact = new AgentArtifact(
                AgentArtifactType.REVIEWS,
                1,
                "reviews:" + selection.detail().product().key() + ":" + selection.offer().key(),
                "Reviews for " + selection.detail().product().title(),
                selection.detail().product().key(),
                selection.offer().key(),
                null, null, null, null,
                json.writeArtifact(reviews)
        );
        return AgentToolExecutionResult.read(
                json.write(reviews),
                reviews.supported()
                        ? "Loaded " + reviews.reviews().size() + " product review(s)."
                        : reviews.message(),
                List.of(artifact)
        );
    }
}
