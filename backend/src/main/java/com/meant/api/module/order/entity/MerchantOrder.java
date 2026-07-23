package com.meant.api.module.order.entity;

import com.meant.api.common.entity.AssignedIdEntity;
import com.meant.api.module.order.constant.OrderState;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "merchant_order",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_merchant_order_remote",
                        columnNames = {"merchant_id", "remote_order_id_hash"}
                )
        }
)
public class MerchantOrder extends AssignedIdEntity<UUID> {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    private UUID userId;

    @Column(nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String merchantDomain;

    private String merchantName;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String remoteOrderId;

    @Column(nullable = false)
    private String remoteOrderIdHash;

    private String orderName;

    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderState state;

    private String remoteFinancialStatus;

    private String remoteFulfillmentStatus;

    private String customerEmail;

    private String orderStatusUrl;

    @Column(nullable = false)
    private String rawOrderResponse;

    private String totalAmount;

    private String subtotalAmount;

    private String currency;

    @Column(nullable = false)
    private Integer totalQuantity;

    private Instant placedAt;

    private Instant remoteUpdatedAt;

    private Instant processedAt;

    private Instant canceledAt;

    private Instant closedAt;

    private String lastWebhookId;

    private String lastWebhookTopic;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Instant refreshedAt;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MerchantOrderLine> lines = new ArrayList<>();

    public void replaceSnapshot(
            UUID userId,
            UUID merchantId,
            String merchantDomain,
            String merchantName,
            String endpoint,
            String remoteOrderId,
            String remoteOrderIdHash,
            String orderName,
            String orderNumber,
            OrderState state,
            String remoteFinancialStatus,
            String remoteFulfillmentStatus,
            String customerEmail,
            String orderStatusUrl,
            String rawOrderResponse,
            String totalAmount,
            String subtotalAmount,
            String currency,
            Integer totalQuantity,
            Instant placedAt,
            Instant remoteUpdatedAt,
            Instant processedAt,
            Instant canceledAt,
            Instant closedAt,
            String lastWebhookId,
            String lastWebhookTopic,
            Instant refreshedAt
    ) {
        if (this.userId == null) {
            this.userId = userId;
        }
        this.merchantId = merchantId;
        this.merchantDomain = merchantDomain;
        this.merchantName = merchantName;
        this.endpoint = endpoint;
        this.remoteOrderId = remoteOrderId;
        this.remoteOrderIdHash = remoteOrderIdHash;
        this.orderName = orderName;
        this.orderNumber = orderNumber;
        this.state = OrderState.transition(this.state, state);
        this.remoteFinancialStatus = remoteFinancialStatus;
        this.remoteFulfillmentStatus = remoteFulfillmentStatus;
        this.customerEmail = customerEmail;
        this.orderStatusUrl = orderStatusUrl;
        this.rawOrderResponse = rawOrderResponse;
        this.totalAmount = totalAmount;
        this.subtotalAmount = subtotalAmount;
        this.currency = currency;
        this.totalQuantity = totalQuantity;
        this.placedAt = placedAt;
        this.remoteUpdatedAt = remoteUpdatedAt;
        this.processedAt = processedAt;
        this.canceledAt = canceledAt;
        this.closedAt = closedAt;
        this.lastWebhookId = lastWebhookId;
        this.lastWebhookTopic = lastWebhookTopic;
        this.updatedAt = refreshedAt;
        this.refreshedAt = refreshedAt;
    }

    public void replaceLines(List<MerchantOrderLine> replacementLines) {
        Map<String, MerchantOrderLine> existingLinesByRemoteId = new HashMap<>();
        lines.forEach(line -> existingLinesByRemoteId.put(line.getRemoteOrderLineId(), line));

        List<MerchantOrderLine> newLines = new ArrayList<>();
        for (MerchantOrderLine replacementLine : replacementLines) {
            MerchantOrderLine existingLine = existingLinesByRemoteId.remove(replacementLine.getRemoteOrderLineId());
            if (existingLine == null) {
                replacementLine.assignOrder(this);
                newLines.add(replacementLine);
            } else {
                existingLine.updateFrom(replacementLine);
            }
        }

        lines.removeIf(line -> existingLinesByRemoteId.containsKey(line.getRemoteOrderLineId()));
        lines.addAll(newLines);
    }
}
