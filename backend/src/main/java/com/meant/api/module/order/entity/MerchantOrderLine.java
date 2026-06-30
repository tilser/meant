package com.meant.api.module.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
        name = "merchant_order_line",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_merchant_order_line_remote",
                        columnNames = {"order_id", "remote_order_line_id"}
                )
        }
)
public class MerchantOrderLine {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MerchantOrder order;

    @Column(nullable = false)
    private String remoteOrderLineId;

    private String productId;

    @Column(nullable = false)
    private String productTitle;

    private String productVariantId;

    private String variantTitle;

    private String sku;

    private String vendor;

    private String imageUrl;

    private String productUrl;

    @Column(nullable = false)
    private Integer quantity;

    private String unitAmount;

    private String totalAmount;

    private String currency;

    @Column(nullable = false)
    private String rawLineResponse;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    void assignOrder(MerchantOrder order) {
        this.order = order;
    }

    void updateFrom(MerchantOrderLine other) {
        this.productId = other.productId;
        this.productTitle = other.productTitle;
        this.productVariantId = other.productVariantId;
        this.variantTitle = other.variantTitle;
        this.sku = other.sku;
        this.vendor = other.vendor;
        this.imageUrl = other.imageUrl;
        this.productUrl = other.productUrl;
        this.quantity = other.quantity;
        this.unitAmount = other.unitAmount;
        this.totalAmount = other.totalAmount;
        this.currency = other.currency;
        this.rawLineResponse = other.rawLineResponse;
        this.updatedAt = other.updatedAt;
    }
}
