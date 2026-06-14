package com.meant.api.module.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "merchant_retrieval_embedding",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_merchant_retrieval_embedding_merchant",
                columnNames = "merchant_id"
        )
)
public class MerchantRetrievalEmbedding {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(nullable = false)
    private String retrievalContent;

    @Column(nullable = false)
    private String retrievalContentHash;

    @Column(nullable = false)
    private String embeddingModel;

    @Column(nullable = false)
    private Instant embeddedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean active;
}
