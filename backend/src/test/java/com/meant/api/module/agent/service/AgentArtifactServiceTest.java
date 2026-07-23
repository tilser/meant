package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentRunEventType;
import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentEventPayload;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentArtifactServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");

    @Test
    void persistsAndAppendsAnOwnedArtifactBatchInExactInputOrder() {
        AgentArtifactReferenceRepository artifacts = mock(AgentArtifactReferenceRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunService runService = mock(AgentRunService.class);
        when(artifacts.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        AgentArtifactService service = new AgentArtifactService(
                artifacts,
                runs,
                runService,
                mock(AgentMetrics.class),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID executionOwner = UUID.randomUUID();

        List<AgentArtifact> input = List.of(
                offer(2, "offer-2", "product-2"),
                offer(1, "offer-1", "product-1")
        );
        var result = service.persist(
                conversationId,
                runId,
                executionOwner,
                UUID.randomUUID(),
                UUID.randomUUID(),
                input
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AgentArtifactReference>> stored =
                ArgumentCaptor.forClass((Class<List<AgentArtifactReference>>) (Class<?>) List.class);
        verify(artifacts, times(1)).saveAll(stored.capture());
        assertThat(stored.getValue())
                .extracting(AgentArtifactReference::getOrdinal)
                .containsExactly(2, 1);
        assertThat(result)
                .extracting(artifact -> artifact.offerKey())
                .containsExactly("offer-2", "offer-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AgentEventPayload>> payloads =
                ArgumentCaptor.forClass((Class<List<AgentEventPayload>>) (Class<?>) List.class);
        verify(runService, times(1)).appendAll(
                org.mockito.ArgumentMatchers.eq(runId),
                org.mockito.ArgumentMatchers.eq(executionOwner),
                org.mockito.ArgumentMatchers.eq(AgentRunEventType.ARTIFACT_UPSERTED),
                payloads.capture()
        );
        assertThat(payloads.getValue())
                .extracting(payload -> payload.artifact().offerKey())
                .containsExactly("offer-2", "offer-1");
    }

    private AgentArtifact offer(int ordinal, String offerKey, String productKey) {
        return new AgentArtifact(
                AgentArtifactType.OFFER,
                ordinal,
                "offer:" + offerKey,
                "Offer " + ordinal,
                productKey,
                offerKey,
                null,
                null,
                null,
                null,
                "{}"
        );
    }
}
