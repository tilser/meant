package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.user.service.UserSavedProductService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ListSavedProductsAgentToolTest {

    @Test
    void keepsOversizedSavedProductArtifactsCompleteWhileBoundingTheModelResult() throws Exception {
        UUID userId = UUID.randomUUID();
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "saved-products@example.test", "Saved", "Products");
        AgentContextProfileService profileService = mock(AgentContextProfileService.class);
        UserSavedProductService savedProductService = mock(UserSavedProductService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentJsonSupport json = new AgentJsonSupport(objectMapper, properties());
        String largeName = "Durable saved product ".repeat(150);
        UserSavedProductResult savedProduct = savedProduct(largeName);
        when(profileService.profile(userId)).thenReturn(profile);
        when(savedProductService.list(any(), any())).thenReturn(List.of(savedProduct));
        ListSavedProductsAgentTool tool = new ListSavedProductsAgentTool(
                json, profileService, savedProductService);

        var result = tool.execute(
                new AgentToolExecutionContext(
                        userId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "show my saved products"
                ),
                "{}"
        );

        assertThat(objectMapper.readTree(result.resultJson()).get("truncated").asBoolean()).isTrue();
        String artifactJson = result.artifacts().getFirst().payloadJson();
        assertThat(artifactJson).hasSizeGreaterThan(256);
        assertThat(objectMapper.readTree(artifactJson).get("name").asText()).isEqualTo(largeName);
        assertThat(objectMapper.readTree(artifactJson).has("truncated")).isFalse();
    }

    private UserSavedProductResult savedProduct(String name) {
        Instant now = Instant.parse("2026-07-19T10:00:00Z");
        return new UserSavedProductResult(
                "saved-product-1",
                "saved-product-hash-1",
                name,
                "Meant Test",
                "Shoes",
                "#e7ebef",
                null,
                null,
                true,
                91,
                129.0,
                12_900L,
                "USD",
                1,
                List.of("running"),
                List.of(),
                "Saved for a future run.",
                List.of(),
                List.of(),
                new UserSavedProductResult.Review(4.7, 42, "Well reviewed."),
                List.of(new UserSavedProductResult.Offer(
                        "offer-1",
                        "Running Shop",
                        129.0,
                        12_900L,
                        "USD",
                        "Delivery calculated by merchant",
                        null,
                        "running.example",
                        "variant-42",
                        "Size 42",
                        true
                )),
                null,
                List.of(),
                null,
                false,
                false,
                null,
                now,
                now
        );
    }

    private AgentProperties properties() {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, 256, 2, Duration.ofSeconds(30), Duration.ofSeconds(10),
                Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }
}
