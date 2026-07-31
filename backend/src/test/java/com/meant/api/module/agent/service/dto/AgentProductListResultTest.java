package com.meant.api.module.agent.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
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
    void searchAdviceCarriesAppliedAndUnsetFilterMetadata() throws Exception {
        AgentProductListResult result = new AgentProductListResult(
                List.of(),
                null,
                false,
                false,
                List.of(),
                null,
                List.of(),
                Map.of("color", new AgentAppliedSearchFilter(
                        List.of("blue"), UserProductSearchDecisionSource.CURRENT_USER_TURN)),
                List.of(UserProductSearchQuestionTarget.SIZE),
                0
        );

        var json = objectMapper.readTree(objectMapper.writeValueAsString(result));

        assertThat(json.at("/appliedFilters/color/values/0").asText()).isEqualTo("blue");
        assertThat(json.get("unsetFilters").get(0).asText()).isEqualTo("SIZE");
        assertThat(json.get("resultCount").asInt()).isZero();
        assertThat(json.has("qualificationId")).isFalse();
    }
}
