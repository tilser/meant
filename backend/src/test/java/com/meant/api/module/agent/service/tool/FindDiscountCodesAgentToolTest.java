package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.AgentContextProfileService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadSelectionService;
import com.meant.api.module.agent.service.dto.AgentProductReadSelection;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.discount.service.DiscountCodeSearchService;
import com.meant.api.module.discount.service.command.SearchDiscountCodesCommand;
import com.meant.api.module.discount.service.dto.DiscountCodeResult;
import com.meant.api.module.discount.service.dto.DiscountCodeSearchResult;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.user.service.UserCommerceContextService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserCommerceContextResult;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class FindDiscountCodesAgentToolTest {

    @Test
    void projectsBuyerSafeCodesAndMerchantOriginIntoModelAndArtifactJson() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                userId, UUID.randomUUID(), null, UUID.randomUUID(), "Find a discount");
        AgentContextProfileService profileService = mock(AgentContextProfileService.class);
        AgentProductReadSelectionService selectionService =
                mock(AgentProductReadSelectionService.class);
        UserCommerceContextService commerceContextService = mock(UserCommerceContextService.class);
        DiscountCodeSearchService discountCodeSearchService = mock(DiscountCodeSearchService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentJsonSupport json = new AgentJsonSupport(objectMapper, properties());
        AgentProductReadSelection selection = selection(merchantId);
        Instant expiresAt = Instant.parse("2026-08-01T00:00:00Z");
        String technicalEndpoint = "https://seller.myshopify.com/api/ucp/mcp";
        String unsafeCode = "https://custom-transport.example/rpc";
        DiscountCodeSearchResult rawResult = new DiscountCodeSearchResult(
                merchantId,
                "merchant.example",
                false,
                Instant.parse("2026-07-24T12:00:00Z"),
                expiresAt,
                List.of(
                        new DiscountCodeResult(
                                "SAVE10",
                                "Save through " + technicalEndpoint,
                                "Validated by seller.myshopify.com",
                                technicalEndpoint,
                                0.9,
                                "Only at seller.myshopify.com",
                                null,
                                expiresAt,
                                "Accepted by " + technicalEndpoint
                        ),
                        new DiscountCodeResult(
                                unsafeCode,
                                "Unsafe code",
                                "Must never be rendered or copied",
                                "https://custom-transport.example/rpc",
                                0.8,
                                null,
                                null,
                                expiresAt,
                                "Accepted"
                        ),
                        new DiscountCodeResult(
                                "SAFE20",
                                "Safe code with an off-origin source",
                                "The code stays usable without exposing its source coordinate",
                                "https://custom-transport.example/rpc",
                                0.7,
                                null,
                                null,
                                expiresAt,
                                "Accepted"
                        )
                )
        );

        when(profileService.profile(userId)).thenReturn(new EnsureUserProfileCommand(
                userId, "buyer@example.test", "Buyer", "Example"));
        when(selectionService.select(context, "product-1", "offer-1", true))
                .thenReturn(selection);
        when(commerceContextService.find(userId)).thenReturn(new UserCommerceContextResult("US"));
        when(discountCodeSearchService.search(any(SearchDiscountCodesCommand.class)))
                .thenReturn(rawResult);

        var result = new FindDiscountCodesAgentTool(
                json,
                profileService,
                selectionService,
                commerceContextService,
                discountCodeSearchService
        ).execute(
                context,
                """
                {"canonicalProductKey":"product-1","selectedOfferKey":"offer-1","quantity":1}
                """
        );

        String modelJson = result.resultJson();
        String artifactJson = result.artifacts().getFirst().payloadJson();
        assertThat(modelJson)
                .doesNotContain(
                        "\"merchantDomain\"",
                        "seller.myshopify.com",
                        "/api/ucp/mcp",
                        unsafeCode,
                        "custom-transport.example"
                );
        assertThat(artifactJson)
                .doesNotContain(
                        "\"merchantDomain\"",
                        "seller.myshopify.com",
                        "/api/ucp/mcp",
                        unsafeCode,
                        "custom-transport.example"
                );

        JsonNode model = objectMapper.readTree(modelJson);
        JsonNode artifact = objectMapper.readTree(artifactJson);
        assertThat(model.path("merchantOrigin").asText()).isEqualTo("merchant.example");
        assertThat(model.path("codes").size()).isEqualTo(2);
        assertThat(model.at("/codes/0/code").asText()).isEqualTo("SAVE10");
        assertThat(model.at("/codes/0/title").asText()).isEqualTo("Save through merchant.example");
        assertThat(model.at("/codes/0/sourceUrl").isNull()).isTrue();
        assertThat(model.at("/codes/1/code").asText()).isEqualTo("SAFE20");
        assertThat(model.at("/codes/1/sourceUrl").isNull()).isTrue();
        assertThat(artifact).isEqualTo(model);
        assertThat(result.safeSummary()).isEqualTo("Found 2 validated discount code(s).");
    }

    private AgentProductReadSelection selection(UUID merchantId) {
        AgentProductReadSelection selection = mock(AgentProductReadSelection.class);
        UserProductDetailResult detail = mock(UserProductDetailResult.class);
        CanonicalProduct product = mock(CanonicalProduct.class);
        Offer offer = mock(Offer.class);
        OfferIdentity identity = mock(OfferIdentity.class);
        ExternalIdentifier variant = mock(ExternalIdentifier.class);
        MerchantIntegrationResult integration = mock(MerchantIntegrationResult.class);
        when(selection.detail()).thenReturn(detail);
        when(selection.offer()).thenReturn(offer);
        when(selection.merchantIntegration()).thenReturn(integration);
        when(detail.product()).thenReturn(product);
        when(product.key()).thenReturn("product-1");
        when(product.title()).thenReturn("Test product");
        when(offer.key()).thenReturn("offer-1");
        when(offer.identity()).thenReturn(identity);
        when(identity.externalVariantIdentity()).thenReturn(variant);
        when(variant.value()).thenReturn("variant-1");
        when(integration.merchantId()).thenReturn(merchantId);
        when(integration.verifiedDomain()).thenReturn("merchant.example");
        return selection;
    }

    private AgentProperties properties() {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, 64_000, 2, Duration.ofSeconds(30), Duration.ofSeconds(10),
                Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }
}
