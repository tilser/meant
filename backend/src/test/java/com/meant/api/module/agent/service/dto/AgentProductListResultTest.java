package com.meant.api.module.agent.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentProductListResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void unrelatedProductListsOmitTheOptionalSimilarityAnchor() throws Exception {
        AgentProductListResult result = new AgentProductListResult(
                List.of(), null, false, false, List.of(), null);

        var json = objectMapper.readTree(objectMapper.writeValueAsString(result));

        assertThat(json.has("similarityAnchor")).isFalse();
    }

    @Test
    void legacyProductListJsonWithoutSimilarityAnchorStillDeserializes() throws Exception {
        AgentProductListResult result = objectMapper.readValue(
                """
                {"products":[],"nextOffset":null,"hasMore":false,"upstreamTruncated":false,
                 "unavailableCanonicalProductKeys":[]}
                """,
                AgentProductListResult.class
        );

        assertThat(result.products()).isEmpty();
        assertThat(result.similarityAnchor()).isNull();
    }

    @Test
    void qualificationQuestionCarriesTheServerContinuationIdentifier() throws Exception {
        UUID qualificationId = UUID.randomUUID();
        AgentProductListResult result = new AgentProductListResult(
                List.of(),
                null,
                false,
                false,
                List.of(),
                null,
                List.of(),
                qualificationId,
                "What boot size do you need?",
                List.of(UserProductSearchQuestionTarget.SIZE)
        );

        var json = objectMapper.readTree(objectMapper.writeValueAsString(result));

        assertThat(json.get("qualificationId").asText()).isEqualTo(qualificationId.toString());
        assertThat(json.get("qualificationTargets").get(0).asText()).isEqualTo("SIZE");
    }
}
