package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import com.meant.api.module.user.service.UserInventoryService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserInventoryItemResult;
import com.meant.api.module.user.service.query.ListUserInventoryItemsQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SearchInventoryAgentToolTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");

    @Test
    void reportsOverflowWhenAOneItemLimitDoesNotProveUniqueness() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentJsonSupport json = new AgentJsonSupport(objectMapper, properties());
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        UserInventoryService inventory = mock(UserInventoryService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                USER_ID, "inventory@example.test", "Inventory", "Shopper");
        when(profiles.profile(USER_ID)).thenReturn(profile);
        when(inventory.list(any(), any(ListUserInventoryItemsQuery.class)))
                .thenReturn(List.of(item("Black quilted jacket"), item("Black leather jacket")));
        SearchInventoryAgentTool tool = new SearchInventoryAgentTool(json, profiles, inventory);

        var result = tool.execute(
                new AgentToolExecutionContext(
                        USER_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "find my black jacket"),
                "{\"query\":\"black jacket\",\"limit\":1}"
        );

        JsonNode output = objectMapper.readTree(result.resultJson());
        assertThat(output.get("items").size()).isEqualTo(1);
        assertThat(output.get("hasMore").asBoolean()).isTrue();
        assertThat(output.get("scanTruncated").asBoolean()).isFalse();
        assertThat(result.artifacts()).singleElement().satisfies(artifact -> {
            JsonNode durable = objectMapper.readTree(artifact.payloadJson());
            assertThat(durable.get("hasMore").asBoolean()).isTrue();
            assertThat(durable.get("scanTruncated").asBoolean()).isFalse();
            assertThat(durable.get("item").get("name").asText()).isEqualTo("Black quilted jacket");
        });
    }

    @Test
    void continuesAfterACappedShortPageToProveWhetherTheMatchIsUnique() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentJsonSupport json = new AgentJsonSupport(objectMapper, properties());
        AgentContextProfileService profiles = mock(AgentContextProfileService.class);
        UserInventoryService inventory = mock(UserInventoryService.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                USER_ID, "inventory@example.test", "Inventory", "Shopper");
        when(profiles.profile(USER_ID)).thenReturn(profile);
        when(inventory.list(any(), any(ListUserInventoryItemsQuery.class)))
                .thenAnswer(invocation -> {
                    ListUserInventoryItemsQuery query = invocation.getArgument(1);
                    return switch (query.page()) {
                        case 0 -> List.of(item("Black quilted jacket"));
                        case 1 -> List.of(item("Black leather jacket"));
                        default -> List.of();
                    };
                });
        SearchInventoryAgentTool tool = new SearchInventoryAgentTool(json, profiles, inventory);

        var result = tool.execute(
                new AgentToolExecutionContext(
                        USER_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "find my black jacket"),
                "{\"query\":\"black jacket\",\"limit\":1}"
        );

        JsonNode output = objectMapper.readTree(result.resultJson());
        assertThat(output.get("items").size()).isEqualTo(1);
        assertThat(output.get("hasMore").asBoolean()).isTrue();
        assertThat(output.get("scanTruncated").asBoolean()).isFalse();
        assertThat(result.artifacts()).singleElement().satisfies(artifact -> {
            JsonNode durable = objectMapper.readTree(artifact.payloadJson());
            assertThat(durable.get("hasMore").asBoolean()).isTrue();
            assertThat(durable.get("scanTruncated").asBoolean()).isFalse();
            assertThat(durable.get("item").get("name").asText()).isEqualTo("Black quilted jacket");
        });
    }

    private UserInventoryItemResult item(String name) {
        return new UserInventoryItemResult(
                UUID.randomUUID(),
                UserInventorySource.MANUAL,
                null,
                null,
                name,
                null,
                UserInventoryCategory.APPAREL,
                null,
                null,
                null,
                null,
                null,
                1,
                "item",
                null,
                null,
                null,
                "Black",
                null,
                List.of(),
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private AgentProperties properties() {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, 24000, 2, Duration.ofSeconds(30), Duration.ofSeconds(10),
                Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }
}
