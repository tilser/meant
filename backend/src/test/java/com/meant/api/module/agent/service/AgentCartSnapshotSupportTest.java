package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.cart.service.BuyerSafeRoutingScopeKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentCartSnapshotSupportTest {

    private final AgentCartSnapshotSupport support = new AgentCartSnapshotSupport(new ObjectMapper());

    @Test
    void projectionNormalizesCartLinesAtTheSharedBoundary() {
        UUID cartId = UUID.randomUUID();
        UUID validLineId = UUID.randomUUID();
        AgentArtifactReference cart = AgentArtifactReference.builder()
                .conversationId(UUID.randomUUID())
                .messageId(UUID.randomUUID())
                .artifactType(AgentArtifactType.CART)
                .ordinal(1)
                .stableKey("cart:" + cartId)
                .cartId(cartId)
                .payloadJson("""
                        {"routingScopeKey":"SHOPIFY:merchant-jackets","lines":[
                          {"cartLineId":"%s","offerKey":"  ","label":"Fallback jacket"},
                          {"cartLineId":"not-a-uuid","offerKey":"offer-invalid","productTitle":"Invalid line"}
                        ]}
                        """.formatted(validLineId))
                .createdAt(Instant.parse("2026-07-19T10:00:00Z"))
                .build();

        AgentCartSnapshotSupport.CartState state = support.project(List.of(cart));

        assertThat(state.current()).hasSize(1);
        assertThat(state.current().getFirst().routingScopeKey())
                .isEqualTo(BuyerSafeRoutingScopeKey.project("SHOPIFY:merchant-jackets"));
        assertThat(state.current().getFirst().lines())
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.cartLineId()).isEqualTo(validLineId);
                    assertThat(line.offerKey()).isNull();
                    assertThat(line.label()).isEqualTo("Fallback jacket");
                });
    }

    @Test
    void projectionJoinsCartLinesToTheirFullCanonicalProductContext() {
        UUID conversationId = UUID.randomUUID();
        UUID cartMessageId = UUID.randomUUID();
        UUID productMessageId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        Instant cartAt = Instant.parse("2026-07-19T10:00:00Z");
        Instant productAt = cartAt.minusSeconds(60);
        AgentArtifactReference cart = AgentArtifactReference.builder()
                .conversationId(conversationId)
                .messageId(cartMessageId)
                .artifactType(AgentArtifactType.CART)
                .ordinal(1)
                .stableKey("cart:" + cartId)
                .cartId(cartId)
                .payloadJson("""
                        {"routingScopeKey":"SHOPIFY:merchant-shirts","lines":[{
                          "cartLineId":"%s","offerKey":"offer-shirt","productTitle":"Oxford Button-Down",
                          "variantTitle":"Navy / Medium"
                        }]}
                        """.formatted(lineId))
                .createdAt(cartAt)
                .build();
        AgentArtifactReference offer = AgentArtifactReference.builder()
                .conversationId(conversationId)
                .messageId(productMessageId)
                .artifactType(AgentArtifactType.OFFER)
                .ordinal(1)
                .stableKey("offer-shirt")
                .canonicalProductKey("product-shirt")
                .offerKey("offer-shirt")
                .payloadJson("""
                        {"key":"offer-shirt","variantTitle":"Navy / Medium",\
                        "selectedOptions":[{"name":"Size","value":"Medium"}]}
                        """)
                .createdAt(productAt)
                .build();
        AgentArtifactReference product = AgentArtifactReference.builder()
                .conversationId(conversationId)
                .messageId(productMessageId)
                .artifactType(AgentArtifactType.PRODUCT)
                .ordinal(1)
                .stableKey("product-shirt")
                .canonicalProductKey("product-shirt")
                .offerKey("offer-shirt")
                .payloadJson("""
                        {"key":"product-shirt","title":"Oxford Button-Down",\
                        "description":"A breathable everyday shirt",\
                        "attributes":[{"group":"apparel","name":"Product type","value":"Shirts"}],\
                        "materials":[{"name":"Organic cotton","percentageBasisPoints":10000}],\
                        "certifications":[{"name":"GOTS certified"}],\
                        "offers":[{"key":"offer-shirt","variantTitle":"Navy / Medium",\
                        "selectedOptions":[{"name":"Size","value":"Medium"}]}]}
                        """)
                .createdAt(productAt)
                .build();

        AgentCartSnapshotSupport.CartLine line = support.project(List.of(cart, offer, product))
                .current().getFirst().lines().getFirst();

        assertThat(line.label()).isEqualTo("Oxford Button-Down");
        assertThat(line.productContext())
                .contains("A breathable everyday shirt")
                .contains("Product type")
                .contains("Shirts")
                .contains("Organic cotton")
                .contains("GOTS certified")
                .contains("Navy / Medium")
                .contains("Size")
                .contains("Medium");
    }
}
