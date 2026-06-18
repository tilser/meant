package com.meant.api.module.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserSettingsLocationId implements Serializable {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String locationCode;

    @Column(nullable = false)
    private String locationCity;
}
