package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.SearchCatalogAgentToolInput;
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SearchCatalogAgentToolTest {

    @Test
    void keepsTheConversationMerchantScopeOutOfTheModelSchemaAndInTheSearchCommand() {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        AgentProductReadResultService results = mock(AgentProductReadResultService.class);
        UserGroupedProductSearchService searches = mock(UserGroupedProductSearchService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserGroupedProductSearchResult result = mock(UserGroupedProductSearchResult.class);
        when(json.readArguments("{}", SearchCatalogAgentToolInput.class))
                .thenReturn(new SearchCatalogAgentToolInput("trail shoes", 0, 10));
        when(profiles.profile(userId)).thenReturn(profile);
        when(result.products()).thenReturn(List.of());
        when(searches.search(eq(profile), any())).thenReturn(result);
        when(json.write(any())).thenReturn("{}");
        SearchCatalogAgentTool tool = new SearchCatalogAgentTool(json, profiles, results, searches);

        tool.execute(context(userId).withMerchantId(merchantId), "{}");

        ArgumentCaptor<SearchUserProductsCommand> command =
                ArgumentCaptor.forClass(SearchUserProductsCommand.class);
        verify(searches).search(eq(profile), command.capture());
        assertThat(command.getValue().merchantId()).isEqualTo(merchantId);
        assertThat(tool.descriptor().inputSchemaJson()).doesNotContain("merchantId");
    }

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
        return context(UUID.randomUUID());
    }

    private AgentToolExecutionContext context(UUID userId) {
        return new AgentToolExecutionContext(
                userId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "find shoes");
    }
}
