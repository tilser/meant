package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
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
        assertThat(objectMapper.readTree(artifactJson).get("name").asText()).isEqualTo(largeName.trim());
        assertThat(objectMapper.readTree(artifactJson).has("truncated")).isFalse();
        assertThat(artifactJson)
                .doesNotContain("manning.myshopify.com", "/api/ucp/mcp", "\"merchantDomain\"");
        assertThat(objectMapper.readTree(artifactJson).at("/satisfies/1").asText())
                .isEqualTo("Buy via nycfactory.com");
        assertThat(objectMapper.readTree(artifactJson).at("/satisfies/2").asText())
                .isEqualTo("Compare nycfactory.com");
    }

    @Test
    void omitsRoutingDomainsAndNeutralizesTechnicalLabelsWithoutChangingOfferKeys() throws Exception {
        UUID userId = UUID.randomUUID();
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "saved-products@example.test", "Saved", "Products");
        AgentContextProfileService profileService = mock(AgentContextProfileService.class);
        UserSavedProductService savedProductService = mock(UserSavedProductService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentJsonSupport json = new AgentJsonSupport(objectMapper, properties(64_000));
        when(profileService.profile(userId)).thenReturn(profile);
        when(savedProductService.list(any(), any()))
                .thenReturn(List.of(savedProduct("Technical saved product")));
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

        String modelJson = result.resultJson();
        String artifactJson = result.artifacts().getFirst().payloadJson();
        assertThat(modelJson)
                .doesNotContain(
                        "manning.myshopify.com",
                        "routing.internal.example",
                        "\"merchantDomain\"",
                        "/api/ucp/mcp"
                );
        assertThat(artifactJson)
                .doesNotContain(
                        "manning.myshopify.com",
                        "routing.internal.example",
                        "\"merchantDomain\"",
                        "/api/ucp/mcp"
                );
        assertThat(objectMapper.readTree(modelJson).at("/products/0/brand").asText())
                .isEqualTo("nycfactory.com");
        assertThat(objectMapper.readTree(modelJson).at("/products/0/offers/0/merchant").asText())
                .isEqualTo("nycfactory.com");
        assertThat(objectMapper.readTree(modelJson).at("/products/0/offers/0/merchantOrigin").asText())
                .isEqualTo("nycfactory.com");
        assertThat(objectMapper.readTree(modelJson).at("/products/0/offers/0/offerKey").asText())
                .isEqualTo("offer-1");
        assertThat(objectMapper.readTree(artifactJson).at("/satisfies/1").asText())
                .isEqualTo("Buy via nycfactory.com");
        assertThat(objectMapper.readTree(artifactJson).at("/details/merchantName").asText())
                .isEqualTo("nycfactory.com");
        assertThat(objectMapper.readTree(artifactJson).at("/details/images/0/url").asText())
                .isEqualTo("https://safe.example/image.jpg");
        assertThat(objectMapper.readTree(artifactJson).at("/details/images/0/altText").asText())
                .isEqualTo("Photo from nycfactory.com");
        assertThat(objectMapper.readTree(artifactJson).at("/details/categories/0/value").asText())
                .isEqualTo("nycfactory.com");
        assertThat(objectMapper.readTree(artifactJson).at("/details/options/0/values/0").asText())
                .isEqualTo("nycfactory.com");
        assertThat(objectMapper.readTree(artifactJson).at("/details/variants/0/title").asText())
                .isEqualTo("nycfactory.com");
        assertThat(objectMapper.readTree(artifactJson).at("/details/attributes/0/value").asText())
                .isEqualTo("nycfactory.com");
        assertThat(objectMapper.readTree(artifactJson).at("/details/messages/0/content").asText())
                .isEqualTo("Use nycfactory.com");
        assertThat(objectMapper.readTree(artifactJson).at("/details/messages/0/url").isNull()).isTrue();
        assertThat(result.artifacts().getFirst().offerKey()).isEqualTo("offer-1");
    }

    private UserSavedProductResult savedProduct(String name) {
        Instant now = Instant.parse("2026-07-19T10:00:00Z");
        return new UserSavedProductResult(
                "saved-product-1",
                "saved-product-hash-1",
                name,
                "manning.myshopify.com",
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
                List.of(
                        "running",
                        "Buy via https://catalog.shopify.com/api/ucp/mcp",
                        "Compare routing.internal.example"
                ),
                List.of(),
                "Saved for a future run.",
                List.of(),
                List.of(),
                new UserSavedProductResult.Review(4.7, 42, "Well reviewed."),
                List.of(new UserSavedProductResult.Offer(
                        "offer-1",
                        "manning.myshopify.com",
                        129.0,
                        12_900L,
                        "USD",
                        "Delivery calculated by merchant",
                        null,
                        "nycfactory.com",
                        "variant-42",
                        "Size 42",
                        true
                )),
                null,
                List.of(),
                null,
                false,
                true,
                details(),
                now,
                now,
                "nycfactory.com",
                List.of(
                        "manning.myshopify.com",
                        "https://catalog.shopify.com/api/ucp/mcp",
                        "routing.internal.example"
                )
        );
    }

    private AgentProperties properties() {
        return properties(256);
    }

    private AgentProperties properties(int maximumResultCharacters) {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, maximumResultCharacters, 2, Duration.ofSeconds(30), Duration.ofSeconds(10),
                Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }

    private RehydratedProductDetails details() {
        return new RehydratedProductDetails(
                "product-1",
                null,
                "Current product",
                null,
                null,
                null,
                List.of(new RehydratedProductDetails.Image(
                        "https://safe.example/image.jpg",
                        "Photo from routing.internal.example"
                )),
                List.of(new RehydratedProductDetails.Media(
                        "image",
                        "https://safe.example/media.jpg",
                        "Media from routing.internal.example",
                        "https://safe.example/preview.jpg"
                )),
                List.of(new RehydratedProductDetails.Category(
                        "routing.internal.example",
                        "Merchant taxonomy"
                )),
                List.of("routing.internal.example", "ordinary"),
                List.of(new RehydratedProductDetails.Option(
                        "Store",
                        List.of("routing.internal.example"),
                        List.of(new RehydratedProductDetails.OptionValue(
                                "routing.internal.example",
                                true,
                                true
                        ))
                )),
                List.of(new RehydratedProductDetails.Variant(
                        "variant-1",
                        "variant-handle",
                        "routing.internal.example",
                        "Variant from routing.internal.example",
                        "https://routing.internal.example/product",
                        "129.00",
                        "USD",
                        null,
                        null,
                        "routing.internal.example",
                        "https://safe.example/variant.jpg",
                        "Variant from routing.internal.example",
                        List.of(),
                        true,
                        List.of(new RehydratedProductDetails.SelectedOption(
                                "Store",
                                "routing.internal.example"
                        )),
                        List.of(new RehydratedProductDetails.Category(
                                "routing.internal.example",
                                "Merchant taxonomy"
                        )),
                        List.of("routing.internal.example"),
                        List.of(new RehydratedProductDetails.Attribute(
                                "Store",
                                "routing.internal.example"
                        ))
                )),
                1,
                null,
                null,
                false,
                new RehydratedProductDetails.Variant(
                        "variant-1",
                        "variant-handle",
                        "routing.internal.example",
                        "Variant from routing.internal.example",
                        "https://routing.internal.example/product",
                        "129.00",
                        "USD",
                        null,
                        null,
                        "routing.internal.example",
                        "https://safe.example/variant.jpg",
                        "Variant from routing.internal.example",
                        List.of(),
                        true,
                        List.of(new RehydratedProductDetails.SelectedOption(
                                "Store",
                                "routing.internal.example"
                        )),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new RehydratedProductDetails.Attribute(
                        "Store",
                        "routing.internal.example"
                )),
                List.of(new RehydratedProductDetails.Message(
                        "INFO",
                        "ROUTE",
                        "routing.internal.example",
                        "text/plain",
                        "Use https://routing.internal.example/private",
                        "INFO",
                        "routing.internal.example",
                        "https://routing.internal.example/image.jpg",
                        "https://routing.internal.example/help"
                )),
                null,
                null,
                null,
                "routing.internal.example",
                "nycfactory.com",
                List.of("https://routing.internal.example/private", "routing.internal.example")
        );
    }
}
