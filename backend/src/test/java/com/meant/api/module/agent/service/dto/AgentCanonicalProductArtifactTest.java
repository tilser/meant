package com.meant.api.module.agent.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadResultService;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.user.controller.response.UserGroupedProductSearchV1Response.CanonicalProductResponse;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.user.service.dto.UserOfferCommercialState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AgentCanonicalProductArtifactTest {

    private static final ProviderIdentity PROVIDER = new ProviderIdentity("shopify");

    @Test
    void matchesTheExistingCanonicalProductWireContract() throws Exception {
        CanonicalProduct product = product();
        UserCanonicalProductPersonalizationResult personalization =
                new UserCanonicalProductPersonalizationResult(
                        "Matches the user's running preference.",
                        List.of("running"),
                        List.of()
                );
        AgentCanonicalProductArtifact artifact = AgentCanonicalProductArtifact.discovery(
                product,
                null,
                personalization,
                Map.of()
        );
        Map<String, UserOfferCommercialState> states = Map.of(
                product.offers().getFirst().key(),
                UserOfferCommercialState.discovery(product.offers().getFirst())
        );
        CanonicalProductResponse established = CanonicalProductResponse.from(
                product,
                null,
                personalization,
                Map.of(),
                states
        );
        ObjectMapper mapper = new ObjectMapper();

        JsonNode artifactJson = mapper.valueToTree(artifact);
        JsonNode establishedJson = mapper.valueToTree(established);
        AgentArtifact durableArtifact = new AgentProductReadResultService(
                new AgentJsonSupport(mapper, properties())
        ).discoveryArtifacts(product, 1, null, personalization, Map.of()).getFirst();
        JsonNode durableArtifactJson = mapper.readTree(durableArtifact.payloadJson());

        assertThat(artifactJson).isEqualTo(establishedJson);
        assertThat(durableArtifact.payloadJson()).hasSizeGreaterThan(256);
        assertThat(durableArtifactJson.has("truncated")).isFalse();
        assertThat(durableArtifactJson.toString()).isEqualTo(establishedJson.toString());
        assertThat(artifactJson.get("recommendedOfferKey").asText())
                .isEqualTo(product.offers().getFirst().key());
        assertThat(artifactJson.at("/offers/0/key").asText())
                .isEqualTo(product.offers().getFirst().key());
        assertThat(artifactJson.at("/offers/0/selectedOptions/0/value").asText()).isEqualTo("42");
        assertThat(artifactJson.at("/offers/0/identity/provider").asText()).isEqualTo("SHOPIFY");
        assertThat(artifactJson.at("/offers/0/identity/merchantScope/type").asText())
                .isEqualTo("EXTERNAL_MERCHANT");
        assertThat(artifactJson.at("/offers/0/commercialState/authority").asText())
                .isEqualTo("DISCOVERY_OBSERVATION");
        assertThat(artifactJson.at("/offers/0/provenance/0/provider").asText())
                .isEqualTo("SHOPIFY");
    }

    @Test
    void similarityDiscoveryPersistsItsGroundedAnchorWithTheProductArtifact() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AgentSimilarityAnchorResult anchor = new AgentSimilarityAnchorResult(
                "canonical:owned-jacket",
                UUID.fromString("00000000-0000-0000-0000-000000000701"),
                "Black Quilted Jacket",
                "similar jackets"
        );

        AgentArtifact artifact = new AgentProductReadResultService(
                new AgentJsonSupport(mapper, properties())
        ).discoveryArtifacts(product(), 1, null, null, Map.of(), anchor).getFirst();
        JsonNode payload = mapper.readTree(artifact.payloadJson());

        assertThat(payload.at("/key").asText()).isEqualTo("canonical:shoe-1");
        assertThat(payload.at("/similarityAnchor/canonicalProductKey").asText())
                .isEqualTo("canonical:owned-jacket");
        assertThat(payload.at("/similarityAnchor/label").asText()).isEqualTo("Black Quilted Jacket");
    }

    private CanonicalProduct product() {
        Instant observedAt = Instant.parse("2026-07-19T10:00:00Z");
        ExternalIdentifier merchant = identifier(ExternalIdentifierType.MERCHANT, "merchant-running");
        ExternalIdentifier product = identifier(ExternalIdentifierType.PRODUCT, "shoe-1");
        ExternalIdentifier variant = identifier(ExternalIdentifierType.VARIANT, "shoe-1-size-42");
        ResultProvenance provenance = new ResultProvenance(
                PROVIDER,
                new DiscoverySourceIdentity(PROVIDER, ResultSourceType.PROVIDER_CATALOG, "GLOBAL_CATALOG"),
                null,
                merchant,
                "running.example",
                product,
                variant,
                new ResultFreshness(observedAt, observedAt.plusSeconds(300)),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "GLOBAL_CATALOG", null)
        );
        List<ProductAttribute> selectedOptions = List.of(
                new ProductAttribute("variant", "Size", "42")
        );
        Offer offer = new Offer(
                new OfferIdentity(
                        PROVIDER,
                        OfferMerchantScope.external(merchant),
                        product,
                        variant,
                        selectedOptions,
                        List.of(),
                        null
                ),
                "Running Shop",
                "Size 42",
                new Money(12_900, "USD"),
                null,
                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 3, null),
                List.of(),
                null,
                List.of(provenance)
        );
        return new CanonicalProduct(
                "canonical:shoe-1",
                "Grounded running shoe",
                "A responsive road running shoe.",
                List.of(),
                List.of(new ProductAttribute(null, "brand", "Meant Test")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(provenance),
                List.of(offer)
        );
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, PROVIDER.value(), value);
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
