package com.meant.api.module.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
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
@Table(name = "shopping_filters")
public class ShoppingFilter {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String polarity;

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private Instant createdAt;
}
