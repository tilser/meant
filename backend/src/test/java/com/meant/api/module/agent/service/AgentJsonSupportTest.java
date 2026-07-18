package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentJsonSupportTest {

    @Test
    void rejectsCallerControlledIdentityAtAnyArgumentDepth() {
        AgentJsonSupport support = new AgentJsonSupport(new ObjectMapper(), properties(1000));

        assertThatThrownBy(() -> support.validateArguments(
                "{\"query\":\"shoes\",\"nested\":{\"owner_id\":\"someone-else\"}}"
        ))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("server controlled");
        assertThatThrownBy(() -> support.validateArguments(
                "{\"items\":[{\"user-id\":\"someone-else\"}]}"
        ))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("server controlled");
    }

    @Test
    void truncatesOversizedResultsIntoAValidBoundedJsonEnvelope() throws Exception {
        AgentJsonSupport support = new AgentJsonSupport(new ObjectMapper(), properties(256));

        String result = support.write("x".repeat(2000));

        assertThat(result.length()).isLessThanOrEqualTo(256);
        var payload = new ObjectMapper().readTree(result);
        assertThat(payload.get("truncated").asBoolean()).isTrue();
        assertThat(payload.get("originalCharacters").asInt()).isGreaterThan(1000);
    }

    @Test
    void canonicalizesObjectFieldOrderRecursivelyForStableIdempotency() {
        AgentJsonSupport support = new AgentJsonSupport(new ObjectMapper(), properties(1000));

        String first = support.validateArguments(
                "{\"quantity\":1,\"item\":{\"offerKey\":\"offer-1\",\"size\":\"42\"}}"
        );
        String reordered = support.validateArguments(
                "{\"item\":{\"size\":\"42\",\"offerKey\":\"offer-1\"},\"quantity\":1}"
        );

        assertThat(first).isEqualTo(reordered);
    }

    private AgentProperties properties(int maximumCharacters) {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, maximumCharacters, 2, Duration.ofSeconds(30), Duration.ofSeconds(10),
                Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }
}
