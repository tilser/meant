package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.service.dto.AgentProductClarification;
import com.meant.api.module.agent.service.dto.AgentVisibleProductReference;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentProductClarificationContextServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentJsonSupport json = mock(AgentJsonSupport.class);
    private final AgentProductClarificationContextService service =
            new AgentProductClarificationContextService(json, objectMapper);

    @Test
    void roundTripsServerAuthoredPendingProductContext() throws Exception {
        when(json.writeArtifact(any())).thenAnswer(invocation ->
                objectMapper.writeValueAsString(invocation.getArgument(0)));
        AgentProductClarification clarification = new AgentProductClarification(
                "prepare_carts",
                "Add the blue hat to my cart.",
                List.of(
                        new AgentVisibleProductReference(1, 5, "product-5", "offer-5", "Navy hat"),
                        new AgentVisibleProductReference(2, 6, "product-6", "offer-6", "Sky blue hat")
                )
        );

        String contentJson = service.serialize(clarification);

        assertThat(contentJson).contains("\"pendingProductClarification\"");
        assertThat(service.deserialize(contentJson)).contains(clarification);
    }

    @Test
    void ignoresMalformedOrIncompletePendingContext() {
        assertThat(service.deserialize("not-json")).isEmpty();
        assertThat(service.deserialize("{\"visibleProducts\":null}")).isEmpty();
        assertThat(service.deserialize("""
                {"pendingProductClarification":{
                  "toolName":"prepare_carts",
                  "originalUserText":"Add one.",
                  "products":[
                    {"visibleOrdinal":1,"resultOrdinal":5,"canonicalProductKey":"product-5",\
                     "recommendedOfferKey":"offer-5","title":"First"},
                    {"visibleOrdinal":1,"resultOrdinal":6,"canonicalProductKey":"product-6",\
                     "recommendedOfferKey":"offer-6","title":"Second"}
                  ]
                }}
                """)).isEmpty();
    }
}
