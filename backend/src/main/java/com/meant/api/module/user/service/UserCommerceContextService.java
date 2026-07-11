package com.meant.api.module.user.service;

import com.meant.api.common.util.CountryCodeNormalizer;
import com.meant.api.module.user.entity.UserSettingsLocation;
import com.meant.api.module.user.repository.UserSettingsLocationRepository;
import com.meant.api.module.user.service.dto.UserCommerceContextResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Narrow public boundary for optional user market context. */
@Service
@RequiredArgsConstructor
public class UserCommerceContextService {
    private final UserSettingsLocationRepository locationRepository;

    @Transactional(readOnly = true)
    public UserCommerceContextResult find(UUID userId) {
        if (userId == null) {
            return new UserCommerceContextResult(null);
        }
        String country = locationRepository.findByIdUserIdOrderByDisplayOrderAsc(userId).stream()
                .map(this::country)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
        return new UserCommerceContextResult(country);
    }

    private String country(UserSettingsLocation location) {
        return location == null || location.getId() == null
                ? null
                : CountryCodeNormalizer.normalizeAlpha2(location.getId().getLocationCode());
    }
}
