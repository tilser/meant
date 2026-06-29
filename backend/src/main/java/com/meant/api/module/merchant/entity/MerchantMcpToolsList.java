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
        name = "merchant_mcp_tools_list",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_merchant_mcp_tools_list_merchant_profile",
                        columnNames = {"merchant_id", "agent_profile_hash"}
                )
        }
)
public class MerchantMcpToolsList {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(nullable = false)
    private String agentProfileHash;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String toolsListRaw;

    @Column(nullable = false)
    private String toolsListHash;

    @Column(nullable = false)
    private Instant capturedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public void updateToolsList(
            String endpoint,
            String toolsListRaw,
            String toolsListHash,
            Instant capturedAt,
            Instant updatedAt
    ) {
        this.endpoint = endpoint;
        this.toolsListRaw = toolsListRaw;
        this.toolsListHash = toolsListHash;
        this.capturedAt = capturedAt;
        this.updatedAt = updatedAt;
    }
}
