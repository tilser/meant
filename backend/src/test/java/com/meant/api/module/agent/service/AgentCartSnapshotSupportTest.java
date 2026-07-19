package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentCartSnapshotSupportTest {

    private final AgentCartSnapshotSupport support = new AgentCartSnapshotSupport(new ObjectMapper());

    @Test
    void projectionNormalizesCartLinesAtTheSharedBoundary() {
        UUID cartId = UUID.randomUUID();
        UUID validLineId = UUID.randomUUID();
        AgentArtifactReference cart = AgentArtifactReference.builder()
                .conversationId(UUID.randomUUID())
                .messageId(UUID.randomUUID())
                .artifactType(AgentArtifactType.CART)
                .ordinal(1)
                .stableKey("cart:" + cartId)
                .cartId(cartId)
                .payloadJson("""
                        {"routingScopeKey":"SHOPIFY:merchant-jackets","lines":[
                          {"cartLineId":"%s","offerKey":"  ","label":"Fallback jacket"},
                          {"cartLineId":"not-a-uuid","offerKey":"offer-invalid","productTitle":"Invalid line"}
                        ]}
                        """.formatted(validLineId))
                .createdAt(Instant.parse("2026-07-19T10:00:00Z"))
                .build();

        AgentCartSnapshotSupport.CartState state = support.project(List.of(cart));

        assertThat(state.current()).hasSize(1);
        assertThat(state.current().getFirst().lines())
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.cartLineId()).isEqualTo(validLineId);
                    assertThat(line.offerKey()).isNull();
                    assertThat(line.label()).isEqualTo("Fallback jacket");
                });
    }
}
