package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentProductListResult;
import com.meant.api.module.agent.service.dto.AgentProductReferenceResult;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.SearchCatalogAgentToolInput;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.service.UserGroupedProductSearchService;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchCatalogAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "search_catalog",
            "Search and rank the grouped commerce catalog. Partial shopping constraints are welcome; search before over-questioning.",
            """
            {"type":"object","properties":{"query":{"type":"string","minLength":1,"maxLength":500},"offset":{"type":"integer","minimum":0,"maximum":99},"limit":{"type":"integer","minimum":1,"maximum":20}},"required":["query"],"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentContextProfileService profileService;
    private final AgentProductReadResultService resultService;
    private final UserGroupedProductSearchService searchService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        SearchCatalogAgentToolInput input = json.readArguments(argumentsJson, SearchCatalogAgentToolInput.class);
        String query = requiredQuery(input.query());
        int offset = input.offset() == null ? UserProductSearchPagination.DEFAULT_OFFSET : input.offset();
        int limit = input.limit() == null ? UserProductSearchPagination.DEFAULT_LIMIT : input.limit();
        if (offset < 0 || offset > UserProductSearchPagination.MAX_OFFSET) {
            throw AgentProductReadToolException.invalid("Offset must be between 0 and 99.");
        }
        if (limit < 1 || limit > UserProductSearchPagination.MAX_LIMIT) {
            throw AgentProductReadToolException.invalid("Limit must be between 1 and 20.");
        }
        UserGroupedProductSearchResult result = searchService.search(
                profileService.profile(context.userId()),
                new SearchUserProductsCommand(context.userId(), query, null, null, null, offset, limit));
        return response(result);
    }

    private AgentToolExecutionResult response(UserGroupedProductSearchResult result) {
        List<CanonicalProduct> products = result.products();
        List<AgentProductReferenceResult> references = IntStream.range(0, products.size())
                .mapToObj(index -> resultService.reference(products.get(index), index + 1))
                .toList();
        List<AgentArtifact> artifacts = IntStream.range(0, products.size())
                .mapToObj(index -> resultService.artifacts(products.get(index), index + 1, products.get(index)))
                .flatMap(List::stream)
                .toList();
        AgentProductListResult output = new AgentProductListResult(
                references, result.nextOffset(), result.hasMore(), result.upstreamTruncated(), List.of());
        return AgentToolExecutionResult.read(
                json.write(output), "Found " + products.size() + " grounded product option(s).", artifacts);
    }

    private String requiredQuery(String query) {
        if (query == null || query.isBlank()) {
            throw AgentProductReadToolException.invalid("A catalog search query is required.");
        }
        String value = query.trim();
        if (value.length() > 500) {
            throw AgentProductReadToolException.invalid("The query must be at most 500 characters.");
        }
        return value;
    }
}
