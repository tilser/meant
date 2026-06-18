package com.meant.api.module.cart.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        name = "cart",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cart_remote_cart_id_hash", columnNames = "remote_cart_id_hash")
        }
)
public class Cart {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String merchantDomain;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String remoteCartId;

    @Column(nullable = false)
    private String remoteCartIdHash;

    private String checkoutUrl;

    private String instructions;

    @Column(nullable = false)
    private String rawCartResponse;

    @Column(nullable = false)
    private Integer totalQuantity;

    private String totalAmount;

    private String subtotalAmount;

    private String currency;

    @Column(nullable = false)
    private boolean active;

    private Instant remoteCreatedAt;

    private Instant remoteUpdatedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Instant refreshedAt;

    @Builder.Default
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartLine> lines = new ArrayList<>();

    public void assignProvider(UUID merchantId, String merchantDomain) {
        this.merchantId = merchantId;
        this.merchantDomain = merchantDomain;
    }

    public void replaceSnapshot(
            String endpoint,
            String remoteCartId,
            String remoteCartIdHash,
            String checkoutUrl,
            String instructions,
            String rawCartResponse,
            Integer totalQuantity,
            String totalAmount,
            String subtotalAmount,
            String currency,
            Instant remoteCreatedAt,
            Instant remoteUpdatedAt,
            Instant refreshedAt
    ) {
        this.endpoint = endpoint;
        this.remoteCartId = remoteCartId;
        this.remoteCartIdHash = remoteCartIdHash;
        this.checkoutUrl = checkoutUrl;
        this.instructions = instructions;
        this.rawCartResponse = rawCartResponse;
        this.totalQuantity = totalQuantity;
        this.totalAmount = totalAmount;
        this.subtotalAmount = subtotalAmount;
        this.currency = currency;
        this.remoteCreatedAt = remoteCreatedAt;
        this.remoteUpdatedAt = remoteUpdatedAt;
        this.active = true;
        this.updatedAt = refreshedAt;
        this.refreshedAt = refreshedAt;
    }

    public void replaceLines(List<CartLine> replacementLines) {
        Map<String, CartLine> existingLinesByRemoteId = new HashMap<>();
        lines.forEach(line -> existingLinesByRemoteId.put(line.getRemoteCartLineId(), line));

        List<CartLine> newLines = new ArrayList<>();
        for (CartLine replacementLine : replacementLines) {
            CartLine existingLine = existingLinesByRemoteId.remove(replacementLine.getRemoteCartLineId());
            if (existingLine == null) {
                replacementLine.assignCart(this);
                newLines.add(replacementLine);
            } else {
                existingLine.updateFrom(replacementLine);
            }
        }

        lines.removeIf(line -> existingLinesByRemoteId.containsKey(line.getRemoteCartLineId()));
        lines.addAll(newLines);
    }
}
