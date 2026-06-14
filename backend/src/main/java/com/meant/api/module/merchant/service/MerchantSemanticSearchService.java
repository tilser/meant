package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.properties.MerchantEmbeddingProperties;
import com.meant.api.module.merchant.repository.MerchantRetrievalEmbeddingVectorRepository;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchCandidate;
import com.meant.api.module.merchant.service.query.SemanticMerchantSearchQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class MerchantSemanticSearchService {

    private final VoyageEmbeddingClient voyageEmbeddingClient;
    private final MerchantRetrievalEmbeddingVectorRepository merchantRetrievalEmbeddingVectorRepository;
    private final MerchantEmbeddingProperties merchantEmbeddingProperties;

    public List<MerchantSemanticSearchCandidate> search(@NotNull @Valid SemanticMerchantSearchQuery query) {
        List<Double> queryEmbedding = voyageEmbeddingClient.embedQuery(query.query());
        return merchantRetrievalEmbeddingVectorRepository.search(
                queryEmbedding,
                merchantEmbeddingProperties.model(),
                query.limit()
        );
    }
}
