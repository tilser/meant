package com.meant.api.module.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
@Table(name = "user_shopping_filters")
public class UserShoppingFilter {

    @EmbeddedId
    private UserShoppingFilterId id;

    @Column(nullable = false)
    private Instant createdAt;

    public static UserShoppingFilter create(UUID userId, String filterId, Instant now) {
        return UserShoppingFilter.builder()
                .id(new UserShoppingFilterId(userId, filterId))
                .createdAt(now)
                .build();
    }
}
