package com.meant.api.module.cart.entity;

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
        name = "cart_line",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cart_line_remote", columnNames = {"cart_id", "remote_cart_line_id"})
        }
)
public class CartLine {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Cart cart;

    @Column(nullable = false)
    private String remoteCartLineId;

    private String productId;

    private String productTitle;

    private String productBrand;

    private String imageUrl;

    private String productUrl;

    @Column(nullable = false)
    private String productVariantId;

    private String variantTitle;

    @Column(nullable = false)
    private Integer quantity;

    private String totalAmount;

    private String subtotalAmount;

    private String currency;

    private String provider;

    private UUID merchantIntegrationId;

    private String externalMerchantId;

    private String externalProductId;

    private String externalVariantId;

    private String offerProductId;

    private String offerVariantId;

    private String offerKey;

    private String canonicalProductKey;

    private String sourceType;

    private String sourceIdentity;

    private String selectedOptionsJson;

    private String componentsJson;

    private String sellingPlanJson;

    private Instant selectedAt;

    @Column(nullable = false)
    private String rawLineResponse;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    void assignCart(Cart cart) {
        this.cart = cart;
    }

    void updateFrom(CartLine other) {
        this.productId = other.productId;
        if (this.offerKey == null) {
            this.productTitle = other.productTitle;
        }
        this.productVariantId = other.productVariantId;
        this.variantTitle = other.variantTitle;
        this.quantity = other.quantity;
        this.totalAmount = other.totalAmount;
        this.subtotalAmount = other.subtotalAmount;
        this.currency = other.currency;
        this.rawLineResponse = other.rawLineResponse;
        this.updatedAt = other.updatedAt;
        if (this.offerKey == null && other.offerKey != null) {
            this.provider = other.provider;
            this.merchantIntegrationId = other.merchantIntegrationId;
            this.externalMerchantId = other.externalMerchantId;
            this.externalProductId = other.externalProductId;
            this.externalVariantId = other.externalVariantId;
            this.offerProductId = other.offerProductId;
            this.offerVariantId = other.offerVariantId;
            this.offerKey = other.offerKey;
            this.canonicalProductKey = other.canonicalProductKey;
            this.sourceType = other.sourceType;
            this.sourceIdentity = other.sourceIdentity;
            this.selectedOptionsJson = other.selectedOptionsJson;
            this.componentsJson = other.componentsJson;
            this.sellingPlanJson = other.sellingPlanJson;
            this.selectedAt = other.selectedAt;
            this.productTitle = other.productTitle;
            this.productBrand = other.productBrand;
            this.imageUrl = other.imageUrl;
            this.productUrl = other.productUrl;
        }
    }

    public void inheritOfferBinding(CartLine other) {
        if (other.offerKey == null) {
            throw new IllegalArgumentException("Cannot inherit an unbound cart line");
        }
        this.provider = other.provider;
        this.merchantIntegrationId = other.merchantIntegrationId;
        this.externalMerchantId = other.externalMerchantId;
        this.externalProductId = other.externalProductId;
        this.externalVariantId = other.externalVariantId;
        this.offerProductId = other.offerProductId;
        this.offerVariantId = other.offerVariantId;
        this.offerKey = other.offerKey;
        this.canonicalProductKey = other.canonicalProductKey;
        this.sourceType = other.sourceType;
        this.sourceIdentity = other.sourceIdentity;
        this.selectedOptionsJson = other.selectedOptionsJson;
        this.componentsJson = other.componentsJson;
        this.sellingPlanJson = other.sellingPlanJson;
        this.selectedAt = other.selectedAt;
        this.productTitle = other.productTitle;
        this.productBrand = other.productBrand;
        this.imageUrl = other.imageUrl;
        this.productUrl = other.productUrl;
    }
}
