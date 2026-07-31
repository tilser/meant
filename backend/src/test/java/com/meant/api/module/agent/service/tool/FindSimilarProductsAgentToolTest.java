package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentInventoryProductAnchorService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadReferenceService;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.AgentProductSearchQualificationService;
import com.meant.api.module.agent.service.AgentSimilaritySearchQualificationService;
import com.meant.api.module.user.service.UserSimilarProductSearchService;
import org.junit.jupiter.api.Test;

class FindSimilarProductsAgentToolTest {

    @Test
    void descriptorExposesStableAnchorsAndAdvisorMetadataWithoutContinuationTokens() {
        FindSimilarProductsAgentTool tool = new FindSimilarProductsAgentTool(
                mock(AgentJsonSupport.class),
                mock(AgentContextProfileService.class),
                mock(AgentProductReadReferenceService.class),
                mock(AgentInventoryProductAnchorService.class),
                mock(AgentProductReadResultService.class),
                mock(AgentProductSearchQualificationService.class),
                mock(AgentSimilaritySearchQualificationService.class),
                mock(UserSimilarProductSearchService.class)
        );

        assertThat(tool.descriptor().inputSchemaJson())
                .contains("canonicalProductKey", "inventoryItemId")
                .doesNotContain("qualificationId", "updatedAt");
        assertThat(tool.descriptor().description())
                .contains("appliedFilters", "unsetFilters");
    }
}
