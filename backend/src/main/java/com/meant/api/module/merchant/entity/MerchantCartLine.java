package com.meant.api.module.merchant.entity;

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
        name = "merchant_cart_line",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_merchant_cart_line_remote", columnNames = {"cart_id", "remote_cart_line_id"})
        }
)
public class MerchantCartLine {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private MerchantCart cart;

    @Column(nullable = false)
    private String remoteCartLineId;

    private String productId;

    private String productTitle;

    @Column(nullable = false)
    private String productVariantId;

    private String variantTitle;

    @Column(nullable = false)
    private Integer quantity;

    private String totalAmount;

    private String subtotalAmount;

    private String currency;

    @Column(nullable = false)
    private String rawLineResponse;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    void assignCart(MerchantCart cart) {
        this.cart = cart;
    }
}
