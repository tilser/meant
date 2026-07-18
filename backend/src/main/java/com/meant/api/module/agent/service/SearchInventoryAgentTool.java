package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentInventoryListResult;
import com.meant.api.module.agent.service.dto.AgentInventoryReferenceResult;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.SearchInventoryAgentToolInput;
import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.service.UserInventoryService;
import com.meant.api.module.user.service.dto.UserInventoryItemResult;
import com.meant.api.module.user.service.query.ListUserInventoryItemsQuery;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchInventoryAgentTool implements AgentTool {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;
    private static final int SCAN_PAGE_SIZE = 100;
    private static final int MAXIMUM_SCANNED_ITEMS = 500;
    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "search_inventory",
            "Find products the current user owns. Use category and a short text query when helpful.",
            """
            {"type":"object","properties":{"query":{"type":"string","maxLength":200},"category":{"type":"string","enum":["APPAREL","PANTRY","HOME","OTHER"]},"restockOnly":{"type":"boolean"},"limit":{"type":"integer","minimum":1,"maximum":20}},"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentContextProfileService profileService;
    private final UserInventoryService userInventoryService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        SearchInventoryAgentToolInput input = json.readArguments(argumentsJson, SearchInventoryAgentToolInput.class);
        int limit = boundedLimit(input.limit());
        UserInventoryCategory category = category(input.category());
        List<UserInventoryItemResult> items = matchingInventory(
                context,
                input,
                category,
                limit
        );
        List<AgentInventoryReferenceResult> references = IntStream.range(0, items.size())
                .mapToObj(index -> reference(items.get(index), index + 1))
                .toList();
        List<AgentArtifact> artifacts = IntStream.range(0, items.size())
                .mapToObj(index -> artifact(items.get(index), index + 1))
                .toList();
        return AgentToolExecutionResult.read(
                json.write(new AgentInventoryListResult(references)),
                "Found " + items.size() + " matching inventory item(s).",
                artifacts
        );
    }

    private AgentInventoryReferenceResult reference(UserInventoryItemResult item, int ordinal) {
        return new AgentInventoryReferenceResult(
                ordinal, item.id(), item.name(), item.brand(), item.category(), item.quantity(), item.unit(),
                item.location(), item.attributes(), item.commerceReference());
    }

    private List<UserInventoryItemResult> matchingInventory(
            AgentToolExecutionContext context,
            SearchInventoryAgentToolInput input,
            UserInventoryCategory category,
            int limit
    ) {
        List<UserInventoryItemResult> matches = new ArrayList<>();
        int scanned = 0;
        for (int page = 0; scanned < MAXIMUM_SCANNED_ITEMS && matches.size() < limit; page++) {
            List<UserInventoryItemResult> batch = userInventoryService.list(
                    profileService.profile(context.userId()),
                    new ListUserInventoryItemsQuery(
                            context.userId(), category, input.restockOnly(), page, SCAN_PAGE_SIZE)
            );
            if (batch.isEmpty()) {
                break;
            }
            for (UserInventoryItemResult item : batch) {
                scanned++;
                if (matches(item, input.query())) {
                    matches.add(item);
                    if (matches.size() == limit || scanned == MAXIMUM_SCANNED_ITEMS) {
                        break;
                    }
                }
            }
        }
        return List.copyOf(matches);
    }

    private AgentArtifact artifact(UserInventoryItemResult item, int ordinal) {
        return new AgentArtifact(
                AgentArtifactType.INVENTORY_ITEM,
                ordinal,
                "inventory:" + item.id(),
                item.name(),
                item.commerceReference() == null ? null : item.commerceReference().canonicalProductKey(),
                item.commerceReference() == null ? null : item.commerceReference().offerKey(),
                item.id(), null, null, null,
                json.write(item)
        );
    }

    private boolean matches(UserInventoryItemResult item, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        String text = String.join(" ", List.of(
                value(item.name()), value(item.brand()), value(item.description()), value(item.notes()),
                String.join(" ", item.attributes() == null ? List.of() : item.attributes())))
                .toLowerCase(Locale.ROOT);
        return text.contains(needle);
    }

    private UserInventoryCategory category(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UserInventoryCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw AgentProductReadToolException.invalid(
                    "Category must be APPAREL, PANTRY, HOME, or OTHER.", exception);
        }
    }

    private int boundedLimit(Integer value) {
        if (value == null) {
            return DEFAULT_LIMIT;
        }
        if (value < 1 || value > MAX_LIMIT) {
            throw AgentProductReadToolException.invalid("Limit must be between 1 and 20.");
        }
        return value;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
