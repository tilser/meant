package com.meant.api.module.merchant.service.task;

import com.meant.api.module.merchant.properties.MerchantEmbeddingProperties;
import com.meant.api.module.merchant.service.MerchantRetrievalEmbeddingService;
import com.meant.api.module.merchant.service.command.GenerateMerchantRetrievalEmbeddingsCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MerchantRetrievalEmbeddingTask {

    private final MerchantRetrievalEmbeddingService merchantRetrievalEmbeddingService;
    private final MerchantEmbeddingProperties merchantEmbeddingProperties;

    @Scheduled(fixedDelayString = "${merchant.embedding.fixed-delay}")
    public void generateRetrievalEmbeddings() {
        if (merchantEmbeddingProperties.apiKey().isBlank()) {
            return;
        }
        merchantRetrievalEmbeddingService.generateRetrievalEmbeddings(
                new GenerateMerchantRetrievalEmbeddingsCommand(
                        merchantEmbeddingProperties.batchSize()
                )
        );
    }
}
