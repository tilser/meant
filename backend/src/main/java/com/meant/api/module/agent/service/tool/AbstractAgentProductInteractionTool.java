package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductInteractionReferenceService;
import com.meant.api.module.agent.service.AgentProductInteractionService;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentProductInteractionResult;
import com.meant.api.module.agent.service.dto.AgentProductInteractionToolInput;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import java.util.List;

abstract class AbstractAgentProductInteractionTool implements AgentTool {

    private final AgentToolDescriptor descriptor;
    private final Operation operation;
    private final AgentJsonSupport json;
    private final AgentProductInteractionService interactionService;

    AbstractAgentProductInteractionTool(
            AgentToolDescriptor descriptor,
            Operation operation,
            AgentJsonSupport json,
            AgentProductInteractionService interactionService
    ) {
        this.descriptor = descriptor;
        this.operation = operation;
        this.json = json;
        this.interactionService = interactionService;
    }

    @Override
    public AgentToolDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        AgentProductInteractionToolInput input = json.readArguments(
                argumentsJson, AgentProductInteractionToolInput.class);
        AgentProductInteractionResult state = operation.execute(
                interactionService, context, input.canonicalProductKey(), input.offerKey());
        String resultJson = json.write(state);
        String displayLabel = state.displayLabel();
        AgentArtifact artifact = new AgentArtifact(
                AgentArtifactType.PRODUCT_STATE,
                1,
                AgentProductInteractionReferenceService.STATE_STABLE_KEY_PREFIX + state.canonicalProductKey(),
                displayLabel,
                state.canonicalProductKey(),
                state.offerKey(),
                null,
                null,
                null,
                null,
                json.writeArtifact(state)
        );
        return new AgentToolExecutionResult(
                resultJson,
                operation.summary(displayLabel),
                List.of(artifact),
                null
        );
    }

    enum Operation {
        PIN {
            @Override
            AgentProductInteractionResult execute(
                    AgentProductInteractionService service,
                    AgentToolExecutionContext context,
                    String canonicalProductKey,
                    String offerKey
            ) {
                return service.pin(context, canonicalProductKey, offerKey);
            }

            @Override
            String summary(String displayLabel) {
                return "Pinned product " + displayLabel + ".";
            }
        },
        UNPIN {
            @Override
            AgentProductInteractionResult execute(
                    AgentProductInteractionService service,
                    AgentToolExecutionContext context,
                    String canonicalProductKey,
                    String offerKey
            ) {
                return service.unpin(context, canonicalProductKey, offerKey);
            }

            @Override
            String summary(String displayLabel) {
                return "Unpinned product " + displayLabel + ".";
            }
        },
        WATCH {
            @Override
            AgentProductInteractionResult execute(
                    AgentProductInteractionService service,
                    AgentToolExecutionContext context,
                    String canonicalProductKey,
                    String offerKey
            ) {
                return service.watch(context, canonicalProductKey, offerKey);
            }

            @Override
            String summary(String displayLabel) {
                return "Watching product " + displayLabel + ".";
            }
        },
        UNWATCH {
            @Override
            AgentProductInteractionResult execute(
                    AgentProductInteractionService service,
                    AgentToolExecutionContext context,
                    String canonicalProductKey,
                    String offerKey
            ) {
                return service.unwatch(context, canonicalProductKey, offerKey);
            }

            @Override
            String summary(String displayLabel) {
                return "Stopped watching product " + displayLabel + ".";
            }
        };

        abstract AgentProductInteractionResult execute(
                AgentProductInteractionService service,
                AgentToolExecutionContext context,
                String canonicalProductKey,
                String offerKey
        );

        abstract String summary(String displayLabel);
    }
}
