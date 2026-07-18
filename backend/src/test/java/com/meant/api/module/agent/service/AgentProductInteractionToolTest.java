package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentProductInteractionResult;
import com.meant.api.module.agent.service.dto.AgentProductInteractionToolInput;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentProductInteractionToolTest {

    @Test
    void mutationToolsHaveStrictIdentityFreeSchemasAndShareTheStateService() {
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentProductInteractionService service = mock(AgentProductInteractionService.class);
        AgentToolExecutionContext context = context();
        when(json.readArguments("{}", AgentProductInteractionToolInput.class))
                .thenReturn(new AgentProductInteractionToolInput("product:boot", "offer:42"));
        AgentProductInteractionResult state = new AgentProductInteractionResult(
                "product:boot", "offer:42", true, "offer:42", Instant.now(),
                false, null, null, Instant.now());
        when(service.pin(context, "product:boot", "offer:42")).thenReturn(state);
        when(json.write(state)).thenReturn("{\"pinned\":true}");
        List<AgentTool> tools = List.of(
                new PinProductAgentTool(json, service),
                new UnpinProductAgentTool(json, service),
                new WatchProductAgentTool(json, service),
                new UnwatchProductAgentTool(json, service)
        );

        var result = tools.getFirst().execute(context, "{}");

        verify(service).pin(context, "product:boot", "offer:42");
        assertThat(result.artifacts()).singleElement().satisfies(artifact -> {
            assertThat(artifact.type()).isEqualTo(AgentArtifactType.PRODUCT_STATE);
            assertThat(artifact.stableKey()).isEqualTo("product-state:product:boot");
            assertThat(artifact.offerKey()).isEqualTo("offer:42");
        });
        assertThat(tools).extracting(tool -> tool.descriptor().name())
                .containsExactly("pin_product", "unpin_product", "watch_product", "unwatch_product");
        assertThat(tools).allSatisfy(tool -> {
            assertThat(tool.descriptor().riskClass()).isEqualTo(AgentToolRisk.REVERSIBLE_MUTATION);
            assertThat(tool.descriptor().inputSchemaJson())
                    .contains("\"additionalProperties\":false")
                    .doesNotContain("userId", "ownerId");
        });
    }

    private AgentToolExecutionContext context() {
        return new AgentToolExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "pin it");
    }
}
