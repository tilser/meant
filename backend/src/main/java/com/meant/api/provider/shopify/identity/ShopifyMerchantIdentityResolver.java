package com.meant.api.provider.shopify.identity;

import static com.meant.api.module.merchant.constant.MerchantIdentityNamespace.DOMAIN;
import static com.meant.api.module.merchant.constant.MerchantIdentityNamespace.SHOPIFY_SHOP;
import static com.meant.api.module.merchant.constant.MerchantIdentityRole.PROVIDER_ID;
import static com.meant.api.module.merchant.constant.MerchantIdentityRole.STOREFRONT_DOMAIN;
import static com.meant.api.module.merchant.constant.MerchantIntegrationProvider.SHOPIFY;

import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.service.dto.MerchantIdentityResolution;
import com.meant.api.module.merchant.service.dto.MerchantIdentityResolutionContext;
import com.meant.api.module.merchant.service.dto.ResolvedMerchantIdentityClaim;
import com.meant.api.module.merchant.service.dto.UcpPaymentHandlerDefinition;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.dto.UcpServiceDefinition;
import com.meant.api.module.merchant.service.port.MerchantIdentityResolver;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Resolves the stable Shopify shop and public storefront represented by a Shopify UCP profile. */
@Component
public class ShopifyMerchantIdentityResolver implements MerchantIdentityResolver {

    private static final String GOOGLE_PAY_HANDLER = "com.google.pay";
    private static final String SHOP_PAY_HANDLER = "dev.shopify.shop_pay";
    private static final Pattern SHOP_GID = Pattern.compile("^gid://shopify/Shop/([1-9][0-9]*)$");
    private static final Pattern SHOP_ID = Pattern.compile("^[1-9][0-9]*$");

    @Override
    public Optional<MerchantIdentityResolution> resolve(MerchantIdentityResolutionContext context) {
        if (context == null || context.profile() == null) {
            return Optional.empty();
        }

        ProfileEvidence evidence = profileEvidence(context.profile());
        if (!supports(context, evidence)) {
            return Optional.empty();
        }

        if (evidence.invalid() || evidence.shopIds().size() != 1) {
            throw identityFailure("Shopify profile did not expose one stable shop ID");
        }

        String shopId = evidence.shopIds().iterator().next();
        String shopGid = shopGid(shopId);
        if (!observedIdentityMatches(context, shopGid)) {
            throw identityFailure("Observed Shopify shop ID does not match the UCP profile");
        }
        if (evidence.storefrontDomains().size() != 1) {
            throw identityFailure("Shopify profile did not expose one canonical storefront domain");
        }

        String canonicalDomain = first(evidence.storefrontDomains());
        String merchantName = evidence.merchantNames().size() == 1 ? first(evidence.merchantNames()) : null;
        List<ResolvedMerchantIdentityClaim> claims = new ArrayList<>();
        claims.add(new ResolvedMerchantIdentityClaim(SHOPIFY_SHOP, shopGid, PROVIDER_ID));
        if (canonicalDomain != null) {
            claims.add(new ResolvedMerchantIdentityClaim(DOMAIN, canonicalDomain, STOREFRONT_DOMAIN));
        }
        return Optional.of(new MerchantIdentityResolution(
                canonicalDomain,
                merchantName,
                claims
        ));
    }

    private boolean supports(MerchantIdentityResolutionContext context, ProfileEvidence evidence) {
        if (context.observedProvider() != null && context.observedProvider() != SHOPIFY) {
            return false;
        }
        return evidence.shopifyProfile()
                || context.observedProvider() == SHOPIFY
                || hasText(context.observedProviderMerchantId())
                        && SHOP_GID.matcher(context.observedProviderMerchantId().trim()).matches();
    }

    private ProfileEvidence profileEvidence(UcpProfile profile) {
        Set<String> shopIds = new LinkedHashSet<>();
        Set<String> storefrontDomains = new LinkedHashSet<>();
        Set<String> merchantNames = new LinkedHashSet<>();
        boolean shopifyProfile = hasHandler(profile, SHOP_PAY_HANDLER);
        boolean invalid = false;

        for (JsonNode config : configs(profile, SHOP_PAY_HANDLER)) {
            String rawShopId = scalarText(config.path("shop_id"));
            if (hasText(rawShopId)) {
                String normalizedShopId = normalizeShopId(rawShopId);
                invalid |= normalizedShopId == null;
                if (normalizedShopId != null) {
                    shopIds.add(normalizedShopId);
                }
            }
        }

        for (JsonNode config : configs(profile, GOOGLE_PAY_HANDLER)) {
            JsonNode merchantInfo = config.path("merchant_info");
            String rawOrigin = scalarText(merchantInfo.path("merchant_origin"));
            if (hasText(rawOrigin)) {
                String normalizedOrigin = normalizeDomain(rawOrigin);
                invalid |= normalizedOrigin == null;
                if (normalizedOrigin != null) {
                    storefrontDomains.add(normalizedOrigin);
                }
            }
            String merchantName = normalizedName(scalarText(merchantInfo.path("merchant_name")));
            if (merchantName != null) {
                merchantNames.add(merchantName);
            }

            for (JsonNode paymentMethod : values(config.path("allowed_payment_methods"))) {
                JsonNode parameters = paymentMethod
                        .path("tokenization_specification")
                        .path("parameters");
                if (!"shopify".equalsIgnoreCase(scalarText(parameters.path("gateway")))) {
                    continue;
                }
                shopifyProfile = true;
                String rawGatewayMerchantId = firstText(
                        scalarText(parameters.path("gatewayMerchantId")),
                        scalarText(parameters.path("gateway_merchant_id"))
                );
                if (!hasText(rawGatewayMerchantId)) {
                    continue;
                }
                String normalizedShopId = normalizeShopId(rawGatewayMerchantId);
                invalid |= normalizedShopId == null;
                if (normalizedShopId != null) {
                    shopIds.add(normalizedShopId);
                }
            }
        }

        return new ProfileEvidence(shopIds, storefrontDomains, merchantNames, shopifyProfile, invalid);
    }

    private boolean hasHandler(UcpProfile profile, String handlerName) {
        return profile.paymentHandlers() != null
                && profile.paymentHandlers().get(handlerName) != null
                && !profile.paymentHandlers().get(handlerName).isEmpty();
    }

    private List<JsonNode> configs(UcpProfile profile, String handlerName) {
        Map<String, List<UcpPaymentHandlerDefinition>> handlers = profile.paymentHandlers();
        if (handlers == null) {
            return List.of();
        }
        List<UcpPaymentHandlerDefinition> definitions = handlers.get(handlerName);
        if (definitions == null) {
            return List.of();
        }
        return definitions.stream()
                .filter(definition -> definition != null && definition.config() != null)
                .map(UcpPaymentHandlerDefinition::config)
                .filter(config -> !config.isNull() && !config.isMissingNode())
                .toList();
    }

    private List<JsonNode> values(JsonNode value) {
        if (value == null || !value.isArray()) {
            return List.of();
        }
        return List.copyOf(value.values());
    }

    private boolean observedIdentityMatches(
            MerchantIdentityResolutionContext context,
            String resolvedShopGid
    ) {
        String observedMerchantId = context.observedProviderMerchantId();
        if (!hasText(observedMerchantId)) {
            return context.observedProvider() != SHOPIFY;
        }
        Matcher matcher = SHOP_GID.matcher(observedMerchantId.trim());
        return matcher.matches() && resolvedShopGid.equals(shopGid(matcher.group(1)));
    }

    private String normalizeShopId(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        Matcher gidMatcher = SHOP_GID.matcher(normalized);
        if (gidMatcher.matches()) {
            return gidMatcher.group(1);
        }
        return SHOP_ID.matcher(normalized).matches() ? normalized : null;
    }

    private String shopGid(String shopId) {
        return "gid://shopify/Shop/" + shopId;
    }

    private String normalizeDomain(String value) {
        if (!hasText(value)) {
            return null;
        }
        String host = value.trim();
        if (host.contains("://")) {
            try {
                URI uri = new URI(host);
                if (!"https".equalsIgnoreCase(uri.getScheme())
                        || uri.getHost() == null
                        || uri.getUserInfo() != null
                        || uri.getPort() != -1
                        || uri.getQuery() != null
                        || uri.getFragment() != null
                        || uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath())) {
                    return null;
                }
                host = uri.getHost();
            } catch (URISyntaxException exception) {
                return null;
            }
        } else if (host.contains("/") || host.contains(":") || host.contains("@")) {
            return null;
        }

        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        try {
            String normalized = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (normalized.startsWith("www.")) {
                normalized = normalized.substring("www.".length());
            }
            return normalized.isBlank() || !normalized.contains(".") ? null : normalized;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalizedName(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.replace("\u0000", "").trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String scalarText(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (value.isString()) {
            return value.stringValue();
        }
        return value.isNumber() ? value.toString() : null;
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first : second;
    }

    private String first(Set<String> values) {
        return values.isEmpty() ? null : values.iterator().next();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private MerchantEnrichmentException identityFailure(String message) {
        return new MerchantEnrichmentException(message);
    }

    private record ProfileEvidence(
            Set<String> shopIds,
            Set<String> storefrontDomains,
            Set<String> merchantNames,
            boolean shopifyProfile,
            boolean invalid
    ) {
    }
}
