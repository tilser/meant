package com.meant.api.module.user.entity;

import com.meant.api.module.user.service.command.UserLocationCommand;
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
@Table(name = "user_settings_locations")
public class UserSettingsLocation {

    @EmbeddedId
    private UserSettingsLocationId id;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String locationCode;

    @Column(nullable = false)
    private String locationCity;

    private String locationRegion;

    private String locationPostalCode;

    private String locationRegionName;

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private Instant createdAt;

    public static UserSettingsLocation create(
            UUID userId,
            UserLocationCommand location,
            int displayOrder,
            Instant now
    ) {
        return UserSettingsLocation.builder()
                .id(new UserSettingsLocationId(userId, location.id()))
                .country(location.country())
                .locationCode(location.code())
                .locationCity(location.city())
                .locationRegion(location.region())
                .locationPostalCode(location.postalCode())
                .locationRegionName(location.regionName())
                .displayOrder(displayOrder)
                .createdAt(now)
                .build();
    }
}
