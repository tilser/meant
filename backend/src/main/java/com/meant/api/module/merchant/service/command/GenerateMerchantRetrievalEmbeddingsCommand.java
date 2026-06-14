package com.meant.api.module.merchant.service.command;

import jakarta.validation.constraints.Positive;

public record GenerateMerchantRetrievalEmbeddingsCommand(
        @Positive
        int batchSize
) {
}
