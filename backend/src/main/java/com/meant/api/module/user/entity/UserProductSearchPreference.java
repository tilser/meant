package com.meant.api.module.user.entity;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
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
        name = "user_product_search_preferences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_product_search_preference_scope_attribute",
                columnNames = {"user_id", "scope", "attribute_name"}
        )
)
public class UserProductSearchPreference {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private String scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private UserProductSearchAttributeName attributeName;

    @Column(nullable = false)
    private String valuesJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static UserProductSearchPreference create(
            UUID userId,
            String scope,
            UserProductSearchAttributeName attributeName,
            String valuesJson,
            Instant now
    ) {
        return UserProductSearchPreference.builder()
                .userId(userId)
                .scope(scope)
                .attributeName(attributeName)
                .valuesJson(valuesJson)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void replaceValues(String nextValuesJson, Instant now) {
        valuesJson = java.util.Objects.requireNonNull(nextValuesJson, "Preference values are required");
        updatedAt = java.util.Objects.requireNonNull(now, "Preference update time is required");
    }
}
