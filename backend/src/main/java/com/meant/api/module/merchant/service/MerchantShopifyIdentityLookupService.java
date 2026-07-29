package com.meant.api.module.merchant.service;

import static com.meant.api.module.merchant.constant.MerchantIdentityNamespace.SHOPIFY_SHOP;
import static com.meant.api.module.merchant.constant.MerchantIdentityRole.PROVIDER_ID;

import com.meant.api.module.merchant.repository.MerchantIdentityRepository;
import com.meant.api.module.merchant.service.query.FindMerchantShopifyIdentityQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Exposes a verified local-merchant to Shopify-Shop identity mapping to provider adapters. */
@Service
@Validated
@RequiredArgsConstructor
public class MerchantShopifyIdentityLookupService {

    private static final Pattern SHOP_GID =
            Pattern.compile("(?i)^gid://shopify/shop/([1-9][0-9]*)$");

    private final MerchantIdentityRepository repository;

    @Transactional(readOnly = true)
    public Optional<String> find(@NotNull @Valid FindMerchantShopifyIdentityQuery query) {
        var shopIds = repository
                .findByMerchantIdAndMerchantActiveTrueAndNamespaceAndRoleOrderByVerifiedAtAsc(
                        query.merchantId(),
                        SHOPIFY_SHOP,
                        PROVIDER_ID
                )
                .stream()
                .map(identity -> canonicalShopGid(identity.getNormalizedValue()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .toList();
        return shopIds.size() == 1 ? Optional.of(shopIds.getFirst()) : Optional.empty();
    }

    private Optional<String> canonicalShopGid(String value) {
        var matcher = SHOP_GID.matcher(value == null ? "" : value.trim());
        return matcher.matches()
                ? Optional.of("gid://shopify/Shop/" + matcher.group(1))
                : Optional.empty();
    }
}
