package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadReferenceService;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentProductListResult;
import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.CompareProductsAgentToolInput;
import com.meant.api.module.user.service.UserCanonicalProductDetailService;
import com.meant.api.module.user.service.dto.UserCanonicalProductsRehydrationResult;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import com.meant.api.module.user.service.query.RehydrateUserCanonicalProductsQuery;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompareProductsAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "compare_products",
            "Rehydrate and compare two to four products previously shown in this conversation. Always use this tool for an explicit product-comparison request so typed inline comparison UI can render; do not answer with prose alone.",
            """
            {"type":"object","properties":{"canonicalProductKeys":{"type":"array","minItems":2,"maxItems":4,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":200}}},"required":["canonicalProductKeys"],"additionalProperties":false}
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
        CompareProductsAgentToolInput input = json.readArguments(argumentsJson, CompareProductsAgentToolInput.class);
        List<String> keys = normalizedKeys(input.canonicalProductKeys());
        keys.forEach(key -> referenceService.requireProduct(context, key));
        UserCanonicalProductsRehydrationResult result = detailService.rehydrate(
                profileService.profile(context.userId()),
                new RehydrateUserCanonicalProductsQuery(context.userId(), keys));
        List<UserProductDetailResult> details = result.products();
        List<AgentProductReferenceResult> references = IntStream.range(0, details.size())
                .mapToObj(index -> resultService.reference(details.get(index).product(), index + 1))
                .toList();
        List<AgentArtifact> artifacts = new ArrayList<>(IntStream.range(0, details.size())
                .mapToObj(index -> resultService.detailArtifacts(details.get(index), index + 1))
                .flatMap(List::stream)
                .toList());
        AgentProductListResult output = new AgentProductListResult(
                references, null, false, false, result.unavailableCanonicalProductKeys());
        artifacts.add(new AgentArtifact(
                AgentArtifactType.COMPARISON,
                1,
                "comparison:" + String.join("|", keys),
                "Product comparison",
                null, null, null, null, null, null,
                json.writeArtifact(output)
        ));
        return AgentToolExecutionResult.read(
                json.write(output),
                "Prepared a grounded comparison of " + details.size() + " product(s).",
                artifacts
        );
    }

    private List<String> normalizedKeys(List<String> values) {
        if (values == null || values.size() < 2 || values.size() > 4) {
            throw AgentProductReadToolException.invalid("Choose two to four products to compare.");
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || value.trim().length() > 200) {
                throw AgentProductReadToolException.invalid("Each product reference must be between 1 and 200 characters.");
            }
            keys.add(value.trim());
        }
        if (keys.size() != values.size()) {
            throw AgentProductReadToolException.invalid("Comparison product references must not contain duplicates.");
        }
        return List.copyOf(keys);
    }
}
