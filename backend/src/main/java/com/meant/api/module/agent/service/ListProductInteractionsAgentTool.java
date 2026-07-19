package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentProductInteractionListResult;
import com.meant.api.module.agent.service.dto.AgentProductInteractionListToolInput;
import com.meant.api.module.agent.service.dto.AgentProductInteractionResult;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListProductInteractionsAgentTool implements AgentTool {

    private static final int DEFAULT_LIMIT = 20;
    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "list_product_interactions",
            "List the current user's persistently pinned and watched products.",
            """
            {"type":"object","properties":{"limit":{"type":"integer","minimum":1,"maximum":50}},"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentProductInteractionService interactionService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        AgentProductInteractionListToolInput input = json.readArguments(
                argumentsJson, AgentProductInteractionListToolInput.class);
        int limit = input.limit() == null ? DEFAULT_LIMIT : input.limit();
        if (limit < 1 || limit > 50) {
            throw AgentProductReadToolException.invalid("Limit must be between 1 and 50.");
        }
        List<AgentProductInteractionResult> products = interactionService.list(context, limit);
        AgentProductInteractionListResult result = new AgentProductInteractionListResult(products);
        List<AgentArtifact> artifacts = IntStream.range(0, products.size())
                .mapToObj(index -> artifact(products.get(index), index + 1))
                .toList();
        return AgentToolExecutionResult.read(
                json.write(result),
                "Loaded " + products.size() + " pinned or watched product(s).",
                artifacts
        );
    }

    private AgentArtifact artifact(AgentProductInteractionResult state, int ordinal) {
        return new AgentArtifact(
                AgentArtifactType.PRODUCT_STATE,
                ordinal,
                AgentProductInteractionReferenceService.STATE_STABLE_KEY_PREFIX + state.canonicalProductKey(),
                state.canonicalProductKey(),
                state.canonicalProductKey(),
                state.offerKey(),
                null,
                null,
                null,
                null,
                json.writeArtifact(state)
        );
    }
}
