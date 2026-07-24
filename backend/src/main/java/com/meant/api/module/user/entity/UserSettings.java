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

    private String clothingFit;

    private String locationCountry;

    private String locationCode;

    private String locationCity;

    private String locationId;

    private String locationRegion;

    private String locationPostalCode;

    private String locationRegionName;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public boolean update(Integer budget, UserLocationCommand location, Instant now) {
        boolean changed = false;
        if (budget != null && updateBudgetValue(budget)) {
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

    public boolean updateBudget(Integer budget, Instant now) {
        if (!updateBudgetValue(budget)) {
            return false;
        }
        this.updatedAt = now;
        return true;
    }

    public boolean updateClothingFit(String clothingFit, Instant now) {
        if (Objects.equals(this.clothingFit, clothingFit)) {
            return false;
        }
        this.clothingFit = clothingFit;
        this.updatedAt = now;
        return true;
    }

    public boolean updatePrimaryLocation(UserLocationCommand location, Instant now) {
        boolean changed = location == null ? clearLocation() : updateLocation(location);
        if (changed) {
            this.updatedAt = now;
        }
        return changed;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    private boolean updateBudgetValue(Integer budget) {
        if (Objects.equals(this.budget, budget)) {
            return false;
        }
        this.budget = budget;
        return true;
    }

    private boolean updateLocation(UserLocationCommand location) {
        if (Objects.equals(this.locationCountry, location.country())
                && Objects.equals(this.locationCode, location.code())
                && Objects.equals(this.locationCity, location.city())
                && Objects.equals(this.locationId, location.id())
                && Objects.equals(this.locationRegion, location.region())
                && Objects.equals(this.locationPostalCode, location.postalCode())
                && Objects.equals(this.locationRegionName, location.regionName())) {
            return false;
        }
        this.locationCountry = location.country();
        this.locationCode = location.code();
        this.locationCity = location.city();
        this.locationId = location.id();
        this.locationRegion = location.region();
        this.locationPostalCode = location.postalCode();
        this.locationRegionName = location.regionName();
        return true;
    }

    private boolean clearLocation() {
        if (this.locationCountry == null
                && this.locationCode == null
                && this.locationCity == null
                && this.locationId == null
                && this.locationRegion == null
                && this.locationPostalCode == null
                && this.locationRegionName == null) {
            return false;
        }
        this.locationCountry = null;
        this.locationCode = null;
        this.locationCity = null;
        this.locationId = null;
        this.locationRegion = null;
        this.locationPostalCode = null;
        this.locationRegionName = null;
        return true;
    }
}
