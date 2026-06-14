package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantRetrievalEmbedding;
import com.meant.api.module.merchant.exception.MerchantEmbeddingException;
import com.meant.api.module.merchant.properties.MerchantEmbeddingProperties;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.repository.MerchantRetrievalEmbeddingRepository;
import com.meant.api.module.merchant.repository.MerchantRetrievalEmbeddingVectorRepository;
import com.meant.api.module.merchant.service.command.GenerateMerchantRetrievalEmbeddingsCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class MerchantRetrievalEmbeddingService {

    private final MerchantRepository merchantRepository;
    private final MerchantRetrievalEmbeddingRepository merchantRetrievalEmbeddingRepository;
    private final MerchantRetrievalEmbeddingVectorRepository merchantRetrievalEmbeddingVectorRepository;
    private final MerchantRetrievalContentBuilder merchantRetrievalContentBuilder;
    private final VoyageEmbeddingClient voyageEmbeddingClient;
    private final MerchantEmbeddingProperties merchantEmbeddingProperties;

    public void generateRetrievalEmbeddings(@NotNull @Valid GenerateMerchantRetrievalEmbeddingsCommand command) {
        List<Merchant> merchants = merchantRepository.findForRetrievalEmbeddingRefresh(
                merchantEmbeddingProperties.model(),
                PageRequest.of(0, command.batchSize())
        );
        List<EmbeddingWorkItem> workItems = new ArrayList<>();

        for (Merchant merchant : merchants) {
            Optional<String> retrievalContent = merchantRetrievalContentBuilder.build(merchant);
            if (retrievalContent.isEmpty()) {
                merchantRetrievalEmbeddingVectorRepository.deactivate(merchant.getId(), Instant.now());
                continue;
            }

            String retrievalContentHash = hash(retrievalContent.get());
            if (embeddingIsCurrent(merchant.getId(), retrievalContentHash)) {
                continue;
            }
            workItems.add(new EmbeddingWorkItem(merchant.getId(), retrievalContent.get(), retrievalContentHash));
        }

        for (List<EmbeddingWorkItem> batch : batches(workItems, merchantEmbeddingProperties.batchSize())) {
            List<List<Double>> embeddings = voyageEmbeddingClient.embedDocuments(batch.stream()
                    .map(EmbeddingWorkItem::retrievalContent)
                    .toList());
            persistEmbeddings(batch, embeddings);
        }
    }

    private boolean embeddingIsCurrent(UUID merchantId, String retrievalContentHash) {
        return merchantRetrievalEmbeddingRepository.findByMerchantId(merchantId)
                .filter(MerchantRetrievalEmbedding::isActive)
                .filter(embedding -> merchantEmbeddingProperties.model().equals(embedding.getEmbeddingModel()))
                .map(MerchantRetrievalEmbedding::getRetrievalContentHash)
                .filter(retrievalContentHash::equals)
                .isPresent();
    }

    private void persistEmbeddings(List<EmbeddingWorkItem> workItems, List<List<Double>> embeddings) {
        if (workItems.size() != embeddings.size()) {
            throw new MerchantEmbeddingException("Embedding count did not match work item count");
        }

        Instant now = Instant.now();
        for (int index = 0; index < workItems.size(); index++) {
            EmbeddingWorkItem workItem = workItems.get(index);
            merchantRetrievalEmbeddingVectorRepository.upsert(
                    workItem.merchantId(),
                    workItem.retrievalContent(),
                    workItem.retrievalContentHash(),
                    embeddings.get(index),
                    merchantEmbeddingProperties.model(),
                    now
            );
        }
    }

    private List<List<EmbeddingWorkItem>> batches(List<EmbeddingWorkItem> workItems, int batchSize) {
        List<List<EmbeddingWorkItem>> batches = new ArrayList<>();
        for (int index = 0; index < workItems.size(); index += batchSize) {
            batches.add(workItems.subList(index, Math.min(index + batchSize, workItems.size())));
        }
        return batches;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new MerchantEmbeddingException("SHA-256 hash algorithm is unavailable", exception);
        }
    }

    private record EmbeddingWorkItem(
            UUID merchantId,
            String retrievalContent,
            String retrievalContentHash
    ) {
    }
}
