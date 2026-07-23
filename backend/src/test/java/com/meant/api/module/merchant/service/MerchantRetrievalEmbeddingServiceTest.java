package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.properties.MerchantEmbeddingProperties;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.repository.MerchantRetrievalEmbeddingRepository;
import com.meant.api.module.merchant.repository.MerchantRetrievalEmbeddingVectorRepository;
import com.meant.api.module.merchant.repository.MerchantRetrievalEmbeddingVectorRepository.MerchantRetrievalEmbeddingUpsert;
import com.meant.api.module.merchant.service.command.GenerateMerchantRetrievalEmbeddingsCommand;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MerchantRetrievalEmbeddingServiceTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void batchesDeactivationsAndPersistsEachEmbeddingClientBatchWithOneRepositoryCall() {
        MerchantRepository merchantRepository = mock(MerchantRepository.class);
        MerchantRetrievalEmbeddingRepository embeddingRepository = mock(MerchantRetrievalEmbeddingRepository.class);
        MerchantRetrievalEmbeddingVectorRepository vectorRepository =
                mock(MerchantRetrievalEmbeddingVectorRepository.class);
        MerchantRetrievalContentBuilder contentBuilder = mock(MerchantRetrievalContentBuilder.class);
        VoyageEmbeddingClient embeddingClient = mock(VoyageEmbeddingClient.class);
        MerchantEmbeddingProperties properties = new MerchantEmbeddingProperties(
                "https://voyage.example",
                "test-key",
                "voyage-3-large",
                "rerank-2.5",
                2,
                2
        );
        MerchantRetrievalEmbeddingService service = new MerchantRetrievalEmbeddingService(
                merchantRepository,
                embeddingRepository,
                vectorRepository,
                contentBuilder,
                embeddingClient,
                properties
        );
        Merchant first = merchant();
        Merchant missingFirst = merchant();
        Merchant second = merchant();
        Merchant missingSecond = merchant();
        Merchant third = merchant();
        List<Merchant> merchants = List.of(first, missingFirst, second, missingSecond, third);
        when(merchantRepository.findForRetrievalEmbeddingRefresh(properties.model(), 10)).thenReturn(merchants);
        when(contentBuilder.buildAll(merchants)).thenReturn(Map.of(
                first.getId(), Optional.of("Categories: Shoes"),
                missingFirst.getId(), Optional.empty(),
                second.getId(), Optional.of("Categories: Home"),
                missingSecond.getId(), Optional.empty(),
                third.getId(), Optional.of("Categories: Books")
        ));
        when(embeddingRepository.findSummariesByMerchantIdIn(anyCollection())).thenReturn(List.of());
        when(embeddingClient.embedDocuments(anyList())).thenAnswer(invocation -> {
            List<String> contents = invocation.getArgument(0);
            return contents.stream()
                    .map(_ -> List.of(1.0d, 0.0d))
                    .toList();
        });

        service.generateRetrievalEmbeddings(new GenerateMerchantRetrievalEmbeddingsCommand(10));

        ArgumentCaptor<Collection<UUID>> deactivatedIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(vectorRepository).deactivateAll(
                deactivatedIdsCaptor.capture(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );
        assertThat(deactivatedIdsCaptor.getValue())
                .containsExactly(missingFirst.getId(), missingSecond.getId());

        ArgumentCaptor<List<MerchantRetrievalEmbeddingUpsert>> upsertsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorRepository, times(2)).upsertAll(upsertsCaptor.capture());
        assertThat(upsertsCaptor.getAllValues())
                .extracting(List::size)
                .containsExactly(2, 1);
        assertThat(upsertsCaptor.getAllValues().stream().flatMap(List::stream).toList())
                .extracting(MerchantRetrievalEmbeddingUpsert::merchantId)
                .containsExactly(first.getId(), second.getId(), third.getId());

        ArgumentCaptor<List<String>> contentBatchCaptor = ArgumentCaptor.forClass(List.class);
        verify(embeddingClient, times(2)).embedDocuments(contentBatchCaptor.capture());
        assertThat(contentBatchCaptor.getAllValues())
                .containsExactly(
                        List.of("Categories: Shoes", "Categories: Home"),
                        List.of("Categories: Books")
                );
    }

    private Merchant merchant() {
        Instant now = Instant.parse("2026-07-23T12:00:00Z");
        return Merchant.builder()
                .id(UUID.randomUUID())
                .domain("merchant-" + UUID.randomUUID() + ".example")
                .ucpUrl("https://merchant.example/.well-known/ucp")
                .ucpVersion("2026-04-08")
                .profileHash("profile-hash")
                .name("Merchant")
                .description("")
                .about("")
                .targetAudience("")
                .profileQuestion("")
                .profileAnswerRaw("")
                .active(true)
                .lastProfiledAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
