package com.meant.api.module.user.entity;

import com.meant.api.common.entity.AssignedIdEntity;
import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.constant.UserInventorySource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
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
        name = "user_inventory_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_inventory_items_source_product",
                        columnNames = {"user_id", "source", "source_product_key"}
                )
        }
)
public class UserInventoryItem extends AssignedIdEntity<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserInventorySource source;

    private String sourceProductKey;

    private String productHash;

    @Column(nullable = false)
    private String name;

    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserInventoryCategory category;

    private String description;

    private String imageUrl;

    private String productUrl;

    private String photoUrl;

    private String photoPath;

    @Column(nullable = false)
    private Integer quantity;

    private String unit;

    private String location;

    private String notes;

    private String size;

    private String color;

    private String material;

    @Column(nullable = false)
    private String attributes;

    @Column(nullable = false)
    private boolean consumable;

    @Column(nullable = false)
    private boolean restockEnabled;

    private Integer restockThreshold;

    private Instant purchasedAt;

    private LocalDate purchasedOn;

    private String provider;

    private UUID merchantIntegrationId;

    private String externalMerchantId;

    private String externalMerchantDomain;

    private String merchantOrigin;

    private String canonicalProductKey;

    private String offerKey;

    private String sourceType;

    private String sourceIdentity;

    private String externalProductId;

    private String externalVariantId;

    private String selectedOptionsJson;

    private UUID sourceCheckoutAttemptId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static UserInventoryItem create(UUID userId, Snapshot snapshot, Instant now) {
        return UserInventoryItem.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .createdAt(now)
                .updatedAt(now)
                .build()
                .replaceSnapshot(snapshot, now);
    }

    public UserInventoryItem replaceSnapshot(Snapshot snapshot, Instant now) {
        this.source = snapshot.source();
        this.sourceProductKey = snapshot.sourceProductKey();
        this.productHash = snapshot.productHash();
        this.name = snapshot.name();
        this.brand = snapshot.brand();
        this.category = snapshot.category();
        this.description = snapshot.description();
        this.imageUrl = snapshot.imageUrl();
        this.productUrl = snapshot.productUrl();
        this.photoUrl = snapshot.photoUrl();
        this.photoPath = snapshot.photoPath();
        this.quantity = snapshot.quantity();
        this.unit = snapshot.unit();
        this.location = snapshot.location();
        this.notes = snapshot.notes();
        this.size = snapshot.size();
        this.color = snapshot.color();
        this.material = snapshot.material();
        this.attributes = snapshot.attributes();
        this.consumable = snapshot.consumable();
        this.restockEnabled = snapshot.restockEnabled();
        this.restockThreshold = snapshot.restockThreshold();
        this.purchasedAt = snapshot.purchasedAt();
        this.purchasedOn = snapshot.purchasedOn();
        this.provider = snapshot.provider();
        this.merchantIntegrationId = snapshot.merchantIntegrationId();
        this.externalMerchantId = snapshot.externalMerchantId();
        this.externalMerchantDomain = snapshot.externalMerchantDomain();
        this.merchantOrigin = snapshot.merchantOrigin();
        this.canonicalProductKey = snapshot.canonicalProductKey();
        this.offerKey = snapshot.offerKey();
        this.sourceType = snapshot.sourceType();
        this.sourceIdentity = snapshot.sourceIdentity();
        this.externalProductId = snapshot.externalProductId();
        this.externalVariantId = snapshot.externalVariantId();
        this.selectedOptionsJson = snapshot.selectedOptionsJson();
        this.sourceCheckoutAttemptId = snapshot.sourceCheckoutAttemptId();
        this.updatedAt = now;
        return this;
    }

    @Builder
    public record Snapshot(
            UserInventorySource source,
            String sourceProductKey,
            String productHash,
            String name,
            String brand,
            UserInventoryCategory category,
            String description,
            String imageUrl,
            String productUrl,
            String photoUrl,
            String photoPath,
            Integer quantity,
            String unit,
            String location,
            String notes,
            String size,
            String color,
            String material,
            String attributes,
            boolean consumable,
            boolean restockEnabled,
            Integer restockThreshold,
            Instant purchasedAt,
            LocalDate purchasedOn,
            String provider,
            UUID merchantIntegrationId,
            String externalMerchantId,
            String externalMerchantDomain,
            String merchantOrigin,
            String canonicalProductKey,
            String offerKey,
            String sourceType,
            String sourceIdentity,
            String externalProductId,
            String externalVariantId,
            String selectedOptionsJson,
            UUID sourceCheckoutAttemptId
    ) {
    }
}
