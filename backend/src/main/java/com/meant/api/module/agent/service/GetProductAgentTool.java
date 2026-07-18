package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentProductListResult;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.GetProductAgentToolInput;
import com.meant.api.module.user.service.UserCanonicalProductDetailService;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import com.meant.api.module.user.service.query.GetUserCanonicalProductDetailQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetProductAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "get_product",
            "Rehydrate current detail and offers for a product previously shown in this conversation.",
            """
            {"type":"object","properties":{"canonicalProductKey":{"type":"string","minLength":1,"maxLength":200},"selectedOfferKey":{"type":"string","minLength":1,"maxLength":200}},"required":["canonicalProductKey"],"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentContextProfileService profileService;
    private final AgentProductReadReferenceService referenceService;
    private final AgentProductReadResultService resultService;
    private final UserCanonicalProductDetailService detailService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        GetProductAgentToolInput input = json.readArguments(argumentsJson, GetProductAgentToolInput.class);
        referenceService.requireProduct(context, input.canonicalProductKey());
        UserProductDetailResult detail = detailService.get(
                profileService.profile(context.userId()),
                new GetUserCanonicalProductDetailQuery(
                        context.userId(), input.canonicalProductKey(), input.selectedOfferKey()));
        AgentProductListResult output = new AgentProductListResult(
                List.of(resultService.reference(detail.product(), 1)), null, false, false, List.of());
        return AgentToolExecutionResult.read(
                json.write(output),
                "Loaded current product detail and " + detail.product().offers().size() + " offer(s).",
                resultService.artifacts(detail.product(), 1, detail)
        );
    }
}
