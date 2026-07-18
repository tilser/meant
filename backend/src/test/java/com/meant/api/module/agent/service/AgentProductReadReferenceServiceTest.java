package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
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

        AgentProductReadReferenceService service = new AgentProductReadReferenceService(repository);

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

        AgentProductReadReferenceService service = new AgentProductReadReferenceService(repository);

        assertThatThrownBy(() -> service.requireInventoryItem(context(), requested))
                .isInstanceOf(AgentException.class);
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

        AgentProductReadReferenceService service = new AgentProductReadReferenceService(repository);

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

        AgentProductReadReferenceService service = new AgentProductReadReferenceService(repository);

        assertThatThrownBy(() -> service.requireCartLine(context(), cartId, lineId))
                .isInstanceOf(AgentException.class);
    }

    private AgentToolExecutionContext context() {
        return new AgentToolExecutionContext(USER_ID, CONVERSATION_ID, UUID.randomUUID(), UUID.randomUUID(), "test");
    }
}
