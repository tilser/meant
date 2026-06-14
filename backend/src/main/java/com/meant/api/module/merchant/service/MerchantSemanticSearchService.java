package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.properties.MerchantEmbeddingProperties;
import com.meant.api.module.merchant.repository.MerchantRetrievalEmbeddingVectorRepository;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchCandidate;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.VoyageRerankResult;
import com.meant.api.module.merchant.service.query.SemanticMerchantSearchQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class MerchantSemanticSearchService {

    private final VoyageEmbeddingClient voyageEmbeddingClient;
    private final VoyageRerankClient voyageRerankClient;
    private final MerchantRetrievalEmbeddingVectorRepository merchantRetrievalEmbeddingVectorRepository;
    private final MerchantEmbeddingProperties merchantEmbeddingProperties;

    public List<MerchantSemanticSearchResult> search(@NotNull @Valid SemanticMerchantSearchQuery query) {
        List<Double> queryEmbedding = voyageEmbeddingClient.embedQuery(query.query());
        List<MerchantSemanticSearchCandidate> candidates = merchantRetrievalEmbeddingVectorRepository.search(
                queryEmbedding,
                merchantEmbeddingProperties.model(),
                query.limit()
        );
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<String> documents = candidates.stream()
                .map(MerchantSemanticSearchCandidate::retrievalContent)
                .toList();
        List<VoyageRerankResult> rerankedCandidates = voyageRerankClient.rerank(query.query(), documents).stream()
                .sorted(Comparator.comparingDouble(VoyageRerankResult::relevanceScore)
                        .reversed()
                        .thenComparingInt(VoyageRerankResult::index))
                .toList();

        return IntStream.range(0, rerankedCandidates.size())
                .mapToObj(index -> toSearchResult(candidates, rerankedCandidates.get(index), index + 1))
                .toList();
    }

    private MerchantSemanticSearchResult toSearchResult(
            List<MerchantSemanticSearchCandidate> candidates,
            VoyageRerankResult rerankResult,
            int rank
    ) {
        MerchantSemanticSearchCandidate candidate = candidates.get(rerankResult.index());
        return new MerchantSemanticSearchResult(
                candidate.merchantId(),
                candidate.domain(),
                candidate.name(),
                candidate.retrievalContent(),
                candidate.score(),
                rerankResult.relevanceScore(),
                rank
        );
    }
}
