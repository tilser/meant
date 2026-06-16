package com.meant.api.module.user.entity;

import com.meant.api.module.user.service.command.UserLocationCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
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
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID userId;

    private Integer budget;

    private String locationCountry;

    private String locationCode;

    private String locationCity;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public boolean update(Integer budget, UserLocationCommand location, Instant now) {
        boolean changed = false;
        if (budget != null && !Objects.equals(this.budget, budget)) {
            this.budget = budget;
            changed = true;
        }
        if (location != null && updateLocation(location)) {
            changed = true;
        }
        if (changed) {
            this.updatedAt = now;
        }
        return changed;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    private boolean updateLocation(UserLocationCommand location) {
        if (Objects.equals(this.locationCountry, location.country())
                && Objects.equals(this.locationCode, location.code())
                && Objects.equals(this.locationCity, location.city())) {
            return false;
        }
        this.locationCountry = location.country();
        this.locationCode = location.code();
        this.locationCity = location.city();
        return true;
    }
}
