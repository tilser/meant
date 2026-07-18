package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.SearchCatalogAgentToolInput;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchCatalogAgentToolTest {

    @Test
    void declaresServerControlledIdentityAndRejectsAnEmptyQueryBeforeDomainExecution() {
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class))
                .thenReturn(new SearchCatalogAgentToolInput(" ", null, null));
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(json, profiles, results, searches);

        assertThat(tool.descriptor().name()).isEqualTo("search_catalog");
        assertThat(tool.descriptor().inputSchemaJson())
                .doesNotContain("userId")
                .contains("\"additionalProperties\":false");
        assertThatThrownBy(() -> tool.execute(context(), "{}"))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("query is required");
        verifyNoInteractions(profiles, results, searches);
    }

    private AgentToolExecutionContext context() {
        return new AgentToolExecutionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "find shoes");
    }
}
