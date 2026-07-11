package com.meant.api.module.cart.service;

import com.meant.api.common.util.CountryCodeNormalizer;
import com.meant.api.module.user.entity.UserSettingsLocation;
import com.meant.api.module.user.repository.UserSettingsLocationRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the UCP {@code context} object (localization + market hints) for cart and checkout
 * tool calls. Shopify allocates inventory per market: without an {@code address_country} hint
 * merchants drop line items as "sold out" even when the variant is purchasable, so every cart
 * and checkout call sends the buyer's country.
 */
@Service
@RequiredArgsConstructor
public class CartBuyerContextService {

    static final String DEFAULT_COUNTRY = "US";

    private final UserSettingsLocationRepository userSettingsLocationRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> buyerContext(UUID userId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("address_country", buyerCountryCode(userId));
        return context;
    }

    private String buyerCountryCode(UUID userId) {
        if (userId == null) {
            return DEFAULT_COUNTRY;
        }
        List<UserSettingsLocation> locations =
                userSettingsLocationRepository.findByIdUserIdOrderByDisplayOrderAsc(userId);
        return locations.stream()
                .map(this::countryCode)
                .filter(code -> code != null)
                .findFirst()
                .orElse(DEFAULT_COUNTRY);
    }

    private String countryCode(UserSettingsLocation location) {
        if (location == null || location.getId() == null) {
            return null;
        }
        return CountryCodeNormalizer.normalizeAlpha2(location.getId().getLocationCode());
    }
}
