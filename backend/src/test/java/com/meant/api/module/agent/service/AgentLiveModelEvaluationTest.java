package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.dto.AgentModelMessage;
import com.meant.api.module.agent.service.dto.AgentModelRequest;
import com.meant.api.module.agent.service.dto.AgentModelToolDefinition;
import com.meant.api.module.agent.service.port.AgentModelGateway;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Protected, budgeted smoke evaluation for OpenRouter. It is absent from ordinary CI and never executes commerce.
 * Run with COMMERCE_AGENT_LIVE_EVAL=true, COMMERCE_AGENT_ENABLED=true, and OPENROUTER_API_KEY configured.
 */
@Tag("live-agent")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "COMMERCE_AGENT_LIVE_EVAL", matches = "true")
class AgentLiveModelEvaluationTest {

    @Autowired
    private AgentModelGateway gateway;

    @Autowired
    private AgentProperties properties;

    @Test
    void configuredModelSelectsTheHarmlessCatalogSchemaWithoutExecutingIt() {
        var response = gateway.turn(
                new AgentModelRequest(
                        properties.model(),
                        List.of(
                                AgentModelMessage.system(
                                        "This is a schema-selection evaluation. Select search_catalog exactly once; "
                                                + "do not claim that it ran."
                                ),
                                AgentModelMessage.user("Find a lightweight summer shirt.")
                        ),
                        List.of(new AgentModelToolDefinition(
                                "search_catalog",
                                "Search the Meant product catalog.",
                                "{\"type\":\"object\",\"additionalProperties\":false,"
                                        + "\"required\":[\"query\"],\"properties\":{\"query\":{\"type\":\"string\"}}}"
                        )),
                        0,
                        256
                ),
                ignored -> { },
                () -> false
        );

        assertThat(response.toolCalls()).singleElement()
                .satisfies(call -> {
                    assertThat(call.name()).isEqualTo("search_catalog");
                    assertThat(call.argumentsJson()).contains("summer shirt");
                });
    }
}
