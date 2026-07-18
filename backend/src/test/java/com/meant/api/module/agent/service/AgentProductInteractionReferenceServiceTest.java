package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentProductInteractionReferenceServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000321");
    private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000322");

    @Test
    void acceptsOnlyAnOfferIssuedForTheExactConversationProduct() {
        AgentArtifactReferenceRepository repository = mock(AgentArtifactReferenceRepository.class);
        AgentArtifactReference product = reference(
                AgentArtifactType.PRODUCT, "product:boot", "offer:default", "Boot");
        AgentArtifactReference exactOffer = reference(
                AgentArtifactType.OFFER, "product:boot", "offer:42", "Boot size 42");
        when(repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "product:boot")).thenReturn(Optional.of(product));
        when(repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer:42")).thenReturn(Optional.of(exactOffer));
        AgentProductInteractionReferenceService service = new AgentProductInteractionReferenceService(repository);

        var result = service.resolve(context(), "product:boot", "offer:42");

        assertThat(result.canonicalProductKey()).isEqualTo("product:boot");
        assertThat(result.offerKey()).isEqualTo("offer:42");
    }

    @Test
    void hidesAnIssuedOfferThatBelongsToAnotherCanonicalProduct() {
        AgentArtifactReferenceRepository repository = mock(AgentArtifactReferenceRepository.class);
        AgentArtifactReference product = reference(
                AgentArtifactType.PRODUCT, "product:boot", "offer:default", "Boot");
        AgentArtifactReference wrongOffer = reference(
                AgentArtifactType.OFFER, "product:shoe", "offer:42", "Other shoe");
        when(repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "product:boot")).thenReturn(Optional.of(product));
        when(repository.findFirstByConversationIdAndStableKeyOrderByCreatedAtDesc(
                CONVERSATION_ID, "offer:42")).thenReturn(Optional.of(wrongOffer));
        AgentProductInteractionReferenceService service = new AgentProductInteractionReferenceService(repository);

        assertThatThrownBy(() -> service.resolve(context(), "product:boot", "offer:42"))
                .isInstanceOf(AgentException.class);
    }

    private AgentArtifactReference reference(
            AgentArtifactType type,
            String canonicalProductKey,
            String offerKey,
            String label
    ) {
        AgentArtifactReference artifact = mock(AgentArtifactReference.class);
        when(artifact.getArtifactType()).thenReturn(type);
        when(artifact.getCanonicalProductKey()).thenReturn(canonicalProductKey);
        when(artifact.getOfferKey()).thenReturn(offerKey);
        when(artifact.getLabel()).thenReturn(label);
        return artifact;
    }

    private AgentToolExecutionContext context() {
        return new AgentToolExecutionContext(
                USER_ID, CONVERSATION_ID, UUID.randomUUID(), UUID.randomUUID(), "watch the boot");
    }
}
