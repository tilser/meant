package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.service.command.VisibleProductContextCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentVisibleProductContextServiceTest {

    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private final AgentArtifactReferenceRepository artifacts = mock(AgentArtifactReferenceRepository.class);
    private final AgentVisibleProductContextService service = new AgentVisibleProductContextService(
            artifacts,
            mock(AgentJsonSupport.class),
            new ObjectMapper()
    );

    @Test
    void resolvesAVisibleCarouselPageInScreenOrderWhileKeepingGlobalResultOrdinals() {
        when(artifacts.findByConversationIdAndMessageIdOrderByOrdinalAsc(CONVERSATION_ID, MESSAGE_ID))
                .thenReturn(List.of(
                        product(1),
                        offer(1),
                        product(5),
                        offer(5),
                        product(6),
                        product(7),
                        product(8)
                ));

        var context = service.resolve(CONVERSATION_ID, new VisibleProductContextCommand(
                MESSAGE_ID,
                List.of("product-5", "product-6", "product-7", "product-8")
        ));

        assertThat(context.sourceMessageId()).isEqualTo(MESSAGE_ID);
        assertThat(context.products())
                .extracting(
                        product -> product.visibleOrdinal(),
                        product -> product.resultOrdinal(),
                        product -> product.canonicalProductKey(),
                        product -> product.recommendedOfferKey())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, 5, "product-5", "offer-5"),
                        org.assertj.core.groups.Tuple.tuple(2, 6, "product-6", "offer-6"),
                        org.assertj.core.groups.Tuple.tuple(3, 7, "product-7", "offer-7"),
                        org.assertj.core.groups.Tuple.tuple(4, 8, "product-8", "offer-8")
                );
    }

    @Test
    void rejectsUnknownDuplicateOrReorderedClientProductKeys() {
        when(artifacts.findByConversationIdAndMessageIdOrderByOrdinalAsc(CONVERSATION_ID, MESSAGE_ID))
                .thenReturn(List.of(product(1), product(2), product(3)));

        for (List<String> invalid : List.of(
                List.of("product-2", "product-1"),
                List.of("product-2", "product-2"),
                List.of("product-other")
        )) {
            assertThatThrownBy(() -> service.resolve(
                    CONVERSATION_ID,
                    new VisibleProductContextCommand(MESSAGE_ID, invalid)
            )).hasMessage("Visible product context does not match the current conversation.");
        }
    }

    private AgentArtifactReference product(int ordinal) {
        return reference(AgentArtifactType.PRODUCT, ordinal, "product-" + ordinal, "offer-" + ordinal);
    }

    private AgentArtifactReference offer(int ordinal) {
        return reference(AgentArtifactType.OFFER, ordinal, "offer-" + ordinal, "offer-" + ordinal);
    }

    private AgentArtifactReference reference(
            AgentArtifactType type,
            int ordinal,
            String stableKey,
            String offerKey
    ) {
        return AgentArtifactReference.builder()
                .conversationId(CONVERSATION_ID)
                .messageId(MESSAGE_ID)
                .artifactType(type)
                .ordinal(ordinal)
                .stableKey(stableKey)
                .label("Product " + ordinal)
                .canonicalProductKey("product-" + ordinal)
                .offerKey(offerKey)
                .payloadJson("{}")
                .createdAt(Instant.parse("2026-07-19T12:00:00Z"))
                .build();
    }
}
