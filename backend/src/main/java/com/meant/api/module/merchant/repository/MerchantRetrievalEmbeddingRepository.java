package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.MerchantRetrievalEmbedding;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantRetrievalEmbeddingRepository extends JpaRepository<MerchantRetrievalEmbedding, UUID> {

    @Query("select embedding from MerchantRetrievalEmbedding embedding where embedding.merchant.id = :merchantId")
    Optional<MerchantRetrievalEmbedding> findByMerchantId(UUID merchantId);

    @Query("""
            select embedding.merchant.id as merchantId,
                   embedding.active as active,
                   embedding.embeddingModel as embeddingModel,
                   embedding.retrievalContentHash as retrievalContentHash
            from MerchantRetrievalEmbedding embedding
            where embedding.merchant.id in :merchantIds
            """)
    List<MerchantRetrievalEmbeddingSummary> findSummariesByMerchantIdIn(
            @Param("merchantIds") Collection<UUID> merchantIds
    );

    interface MerchantRetrievalEmbeddingSummary {

        UUID getMerchantId();

        boolean isActive();

        String getEmbeddingModel();

        String getRetrievalContentHash();
    }
}
