package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.MerchantRetrievalEmbedding;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MerchantRetrievalEmbeddingRepository extends JpaRepository<MerchantRetrievalEmbedding, UUID> {

    @Query("select embedding from MerchantRetrievalEmbedding embedding where embedding.merchant.id = :merchantId")
    Optional<MerchantRetrievalEmbedding> findByMerchantId(UUID merchantId);
}
