package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentInventoryArtifactKind;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentInventorySearchArtifact;
import com.meant.api.module.agent.service.dto.AgentInventorySelectedItemArtifact;
import com.meant.api.module.user.service.dto.UserInventoryItemResult;
import com.meant.api.module.user.service.dto.UserInventoryProductRehydrationResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentProductReadReferenceServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Test
    void resolvesOnlyAProductIssuedInTheCurrentConversation() {
        AgentArtifactReferenceRepository repository = mock(AgentArtifactReferenceRepository.class);
        AgentArtifactReference artifact = mock(AgentArtifactReference.class);
        when(artifact.getArtifactType()).thenReturn(AgentArtifactType.PRODUCT);
        when(artifact.getCanonicalProductKey()).thenReturn("product:one");
        when(repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "product:one")).thenReturn(Optional.of(artifact));

        AgentProductReadReferenceService service = service(repository);

        assertThat(service.requireProduct(context(), "product:one")).isSameAs(artifact);
        assertThatThrownBy(() -> service.requireProduct(context(), "product:invented"))
                .isInstanceOf(AgentException.class);
    }

    @Test
    void rejectsAnInventoryIdWhoseArtifactDoesNotMatch() {
        UUID requested = UUID.fromString("00000000-0000-0000-0000-000000000103");
        AgentArtifactReferenceRepository repository = mock(AgentArtifactReferenceRepository.class);
        AgentArtifactReference artifact = mock(AgentArtifactReference.class);
        when(artifact.getArtifactType()).thenReturn(AgentArtifactType.INVENTORY_ITEM);
        when(artifact.getInventoryItemId()).thenReturn(UUID.randomUUID());
        when(repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "inventory:" + requested)).thenReturn(Optional.of(artifact));

        AgentProductReadReferenceService service = service(repository);

        assertThatThrownBy(() -> service.requireInventoryItem(context(), requested))
                .isInstanceOf(AgentException.class);
    }

    @Test
    void resolvesOnlyACompleteSingletonInventoryResultForSimilarity() {
        UUID requested = UUID.fromString("00000000-0000-0000-0000-000000000104");
        UUID messageId = UUID.fromString("00000000-0000-0000-0000-000000000105");
        AgentArtifactReferenceRepository repository = mock(AgentArtifactReferenceRepository.class);
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentArtifactReference artifact = inventoryArtifact(requested, messageId, "{\"item\":{}}");
        when(repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "inventory:" + requested)).thenReturn(Optional.of(artifact));
        when(repository.findByConversationIdAndMessageIdOrderByOrdinalAsc(CONVERSATION_ID, messageId))
                .thenReturn(List.of(artifact));
        when(json.readArtifact(artifact.getPayloadJson(), AgentInventorySearchArtifact.class))
                .thenReturn(Optional.of(new AgentInventorySearchArtifact(
                        AgentInventoryArtifactKind.SEARCH_RESULT,
                        mock(UserInventoryItemResult.class),
                        false,
                        false)));
        AgentProductReadReferenceService service = new AgentProductReadReferenceService(repository, json);

        assertThat(service.requireSoleInventoryItem(context(), requested)).isSameAs(artifact);
    }

    @Test
    void rejectsAReportedSingletonWhenTheInventorySearchHasMoreMatches() {
        UUID requested = UUID.fromString("00000000-0000-0000-0000-000000000106");
        UUID messageId = UUID.fromString("00000000-0000-0000-0000-000000000107");
        AgentArtifactReferenceRepository repository = mock(AgentArtifactReferenceRepository.class);
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentArtifactReference artifact = inventoryArtifact(requested, messageId, "{\"item\":{}}");
        when(repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "inventory:" + requested)).thenReturn(Optional.of(artifact));
        when(repository.findByConversationIdAndMessageIdOrderByOrdinalAsc(CONVERSATION_ID, messageId))
                .thenReturn(List.of(artifact));
        when(json.readArtifact(artifact.getPayloadJson(), AgentInventorySearchArtifact.class))
                .thenReturn(Optional.of(new AgentInventorySearchArtifact(
                        AgentInventoryArtifactKind.SEARCH_RESULT,
                        mock(UserInventoryItemResult.class),
                        true,
                        false)));
        AgentProductReadReferenceService service = new AgentProductReadReferenceService(repository, json);

        assertThatThrownBy(() -> service.requireSoleInventoryItem(context(), requested))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("choose an item");
    }

    @Test
    void rejectsAnInventoryItemFromAMultiItemResultSet() {
        UUID requested = UUID.fromString("00000000-0000-0000-0000-000000000108");
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000109");
        UUID messageId = UUID.fromString("00000000-0000-0000-0000-000000000110");
        AgentArtifactReferenceRepository repository = mock(AgentArtifactReferenceRepository.class);
        AgentArtifactReference artifact = inventoryArtifact(requested, messageId, "{}");
        AgentArtifactReference sibling = inventoryArtifact(other, messageId, "{}");
        when(repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "inventory:" + requested)).thenReturn(Optional.of(artifact));
        when(repository.findByConversationIdAndMessageIdOrderByOrdinalAsc(CONVERSATION_ID, messageId))
                .thenReturn(List.of(artifact, sibling));
        AgentProductReadReferenceService service = service(repository);

        assertThatThrownBy(() -> service.requireSoleInventoryItem(context(), requested))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("ambiguous");
    }

    @Test
    void acceptsAnExplicitlySelectedInventoryItemArtifact() {
        UUID requested = UUID.fromString("00000000-0000-0000-0000-000000000111");
        UUID messageId = UUID.fromString("00000000-0000-0000-0000-000000000112");
        AgentArtifactReferenceRepository repository = mock(AgentArtifactReferenceRepository.class);
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentArtifactReference artifact = inventoryArtifact(requested, messageId, "{\"kind\":\"SELECTED_ITEM\"}");
        when(repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "inventory:" + requested)).thenReturn(Optional.of(artifact));
        when(repository.findByConversationIdAndMessageIdOrderByOrdinalAsc(CONVERSATION_ID, messageId))
                .thenReturn(List.of(artifact));
        when(json.readArtifact(artifact.getPayloadJson(), AgentInventorySearchArtifact.class))
                .thenReturn(Optional.empty());
        when(json.readArtifact(artifact.getPayloadJson(), AgentInventorySelectedItemArtifact.class))
                .thenReturn(Optional.of(new AgentInventorySelectedItemArtifact(
                        AgentInventoryArtifactKind.SELECTED_ITEM,
                        mock(UserInventoryProductRehydrationResult.class))));
        AgentProductReadReferenceService service = new AgentProductReadReferenceService(repository, json);

        assertThat(service.requireSoleInventoryItem(context(), requested)).isSameAs(artifact);
    }

    @Test
    void rejectsALegacyOrMalformedSingletonWithoutExplicitProvenance() {
        UUID requested = UUID.fromString("00000000-0000-0000-0000-000000000113");
        UUID messageId = UUID.fromString("00000000-0000-0000-0000-000000000114");
        AgentArtifactReferenceRepository repository = mock(AgentArtifactReferenceRepository.class);
        AgentArtifactReference artifact = inventoryArtifact(requested, messageId, "{\"name\":\"Legacy jacket\"}");
        when(repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "inventory:" + requested)).thenReturn(Optional.of(artifact));
        when(repository.findByConversationIdAndMessageIdOrderByOrdinalAsc(CONVERSATION_ID, messageId))
                .thenReturn(List.of(artifact));
        AgentProductReadReferenceService service = service(repository);

        assertThatThrownBy(() -> service.requireSoleInventoryItem(context(), requested))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("ambiguous");
    }

    @Test
    void resolvesOnlyAnExactOfferIssuedInTheCurrentConversation() {
        AgentArtifactReferenceRepository repository = mock(AgentArtifactReferenceRepository.class);
        AgentArtifactReference artifact = mock(AgentArtifactReference.class);
        when(artifact.getArtifactType()).thenReturn(AgentArtifactType.OFFER);
        when(artifact.getCanonicalProductKey()).thenReturn("product:one");
        when(artifact.getOfferKey()).thenReturn("offer:one-blue");
        when(repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer:one-blue")).thenReturn(Optional.of(artifact));

        AgentProductReadReferenceService service = service(repository);

        assertThat(service.requireOffer(context(), "product:one", "offer:one-blue")).isSameAs(artifact);
        assertThatThrownBy(() -> service.requireOffer(context(), "product:other", "offer:one-blue"))
                .isInstanceOf(AgentException.class);
        assertThatThrownBy(() -> service.requireOffer(context(), "offer:invented"))
                .isInstanceOf(AgentException.class);
    }

    @Test
    void rejectsACartLineIssuedForADifferentCart() {
        UUID cartId = UUID.randomUUID();
        UUID otherCartId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        AgentArtifactReferenceRepository repository = mock(AgentArtifactReferenceRepository.class);
        AgentArtifactReference cart = mock(AgentArtifactReference.class);
        when(cart.getArtifactType()).thenReturn(AgentArtifactType.CART);
        when(cart.getCartId()).thenReturn(cartId);
        when(repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "cart:" + cartId)).thenReturn(Optional.of(cart));
        AgentArtifactReference line = mock(AgentArtifactReference.class);
        when(line.getArtifactType()).thenReturn(AgentArtifactType.CART_LINE);
        when(line.getCartId()).thenReturn(otherCartId);
        when(line.getCartLineId()).thenReturn(lineId);
        when(repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "cart-line:" + lineId)).thenReturn(Optional.of(line));

        AgentProductReadReferenceService service = service(repository);

        assertThatThrownBy(() -> service.requireCartLine(context(), cartId, lineId))
                .isInstanceOf(AgentException.class);
    }

    private AgentToolExecutionContext context() {
        return new AgentToolExecutionContext(USER_ID, CONVERSATION_ID, UUID.randomUUID(), UUID.randomUUID(), "test");
    }

    private AgentProductReadReferenceService service(AgentArtifactReferenceRepository repository) {
        return new AgentProductReadReferenceService(repository, mock(AgentJsonSupport.class));
    }

    private AgentArtifactReference inventoryArtifact(UUID inventoryItemId, UUID messageId, String payloadJson) {
        return AgentArtifactReference.builder()
                .conversationId(CONVERSATION_ID)
                .messageId(messageId)
                .artifactType(AgentArtifactType.INVENTORY_ITEM)
                .ordinal(1)
                .stableKey("inventory:" + inventoryItemId)
                .inventoryItemId(inventoryItemId)
                .payloadJson(payloadJson)
                .build();
    }
}
