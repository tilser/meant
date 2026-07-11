package com.meant.api.provider.shopify.cart;

import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.cart.service.port.ExternalOfferCartRoutingProvider;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.service.MerchantCartProviderLookupService;
import com.meant.api.module.merchant.service.MerchantOutboundUrlValidator;
import com.meant.api.module.merchant.service.MerchantUcpProfileObservationService;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.merchant.service.dto.MerchantUcpProfileObservation;
import com.meant.api.module.merchant.service.dto.UcpServiceDefinition;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import java.net.IDN;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Resolves Shopify seller domains through the existing merchant dataset, with bounded miss discovery. */
@Component
public class ShopifyExternalOfferCartRoutingProvider implements ExternalOfferCartRoutingProvider {
    private static final String SHOPPING_SERVICE = "dev.ucp.shopping";
    private static final String CART_CAPABILITY_PREFIX = "dev.ucp.shopping.cart";

    private final ShopifyCartProperties properties;
    private final MerchantCartProviderLookupService merchantLookup;
    private final MerchantUcpProfileObservationService profileObservations;
    private final MerchantOutboundUrlValidator urlValidator;

    public ShopifyExternalOfferCartRoutingProvider(
            ShopifyCartProperties properties,
            MerchantCartProviderLookupService merchantLookup,
            MerchantUcpProfileObservationService profileObservations,
            MerchantOutboundUrlValidator urlValidator
    ) {
        this.properties = properties;
        this.merchantLookup = merchantLookup;
        this.profileObservations = profileObservations;
        this.urlValidator = urlValidator;
    }

    @Override
    public boolean supports(ResolvedSelectedOffer offer) {
        return "SHOPIFY".equals(offer.identity().provider().value())
                && offer.rehydratedReference().localRouting() == null;
    }

    @Override
    public Optional<CartRoutingTarget> resolve(ResolvedSelectedOffer offer) {
        ExternalIdentifier merchant = offer.identity().merchantScope().externalMerchantIdentity();
        String domain = normalizedDomain(offer.rehydratedReference().externalMerchantDomain());
        if (merchant == null || domain == null) {
            return Optional.empty();
        }
        return route(domain, merchant.value());
    }

    @Override
    public boolean supportsPersisted(CartRoutingTarget target) {
        return target.provider() == MerchantIntegrationProvider.SHOPIFY
                && target.merchantIntegrationId() == null;
    }

    @Override
    public Optional<CartRoutingTarget> restore(CartRoutingTarget target) {
        String domain = normalizedDomain(target.merchantProvider().domain());
        return domain == null || target.externalMerchantId() == null
                ? Optional.empty() : route(domain, target.externalMerchantId());
    }

    private Optional<CartRoutingTarget> route(String domain, String externalMerchantId) {
        try {
            Optional<MerchantCartProvider> match = merchantLookup.findActiveByCanonicalDomain(domain);
            if (match.isPresent()) {
                Observation observation = storedObservation(domain, match.get())
                        .orElseGet(() -> refreshObservation(domain));
                return Optional.of(externalTarget(domain, externalMerchantId, observation));
            }
            return discovered(domain, externalMerchantId);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public CartRoutingTarget refresh(CartRoutingTarget target) {
        String domain = normalizedDomain(target.merchantProvider().domain());
        if (domain == null) {
            throw new MerchantEnrichmentException("Stored Shopify cart route has no merchant domain");
        }
        Observation observation = refreshObservation(domain);
        return new CartRoutingTarget(target.scopeKey(), target.provider(), null, target.externalMerchantId(),
                externalProvider(domain, observation));
    }

    private Observation refreshObservation(String domain) {
        URI profileOrigin = profileOrigin(domain);
        return executable(profileObservations.refresh(domain, profileOrigin));
    }

    private Optional<CartRoutingTarget> discovered(String domain, String externalMerchantId) {
        try {
            URI profileOrigin = profileOrigin(domain);
            Observation observation = executable(profileObservations.observe(domain, profileOrigin));
            return Optional.of(externalTarget(domain, externalMerchantId, observation));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private URI profileOrigin(String domain) {
        return urlValidator.validateMerchantUrl(domain, "https://" + domain + "/.well-known/ucp");
    }

    private Optional<Observation> storedObservation(String domain, MerchantCartProvider provider) {
        if (provider.profileCapturedAt() == null
                || !provider.profileCapturedAt().isAfter(Instant.now().minus(properties.profileFreshness()))
                || provider.advertisedCapabilities().stream()
                        .noneMatch(name -> name.startsWith(CART_CAPABILITY_PREFIX))
                || provider.advertisedMcpEndpoint() == null
                || provider.advertisedMcpEndpoint().isBlank()) {
            return Optional.empty();
        }
        try {
            URI endpoint = urlValidator.validateMerchantUrl(domain, provider.advertisedMcpEndpoint());
            URI profileEndpoint = provider.profileMcpEndpoint() == null || provider.profileMcpEndpoint().isBlank()
                    ? profileOrigin(domain)
                    : urlValidator.validateMerchantUrl(domain, provider.profileMcpEndpoint());
            return Optional.of(new Observation(profileEndpoint, endpoint));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private CartRoutingTarget externalTarget(String domain, String externalMerchantId, Observation observation) {
        return new CartRoutingTarget(
                "SHOPIFY:merchant:" + externalMerchantId + ":domain:" + domain,
                MerchantIntegrationProvider.SHOPIFY,
                null,
                externalMerchantId,
                externalProvider(domain, observation)
        );
    }

    private MerchantCartProvider externalProvider(String domain, Observation observation) {
        return new MerchantCartProvider(
                null, domain, observation.endpoint().toString(), observation.profileEndpoint().toString(),
                List.of(), MerchantExecutionPolicy.unavailable(), Instant.now(), Set.of(CART_CAPABILITY_PREFIX));
    }

    private Observation executable(MerchantUcpProfileObservation profile) {
        if (!hasCartCapability(profile)) {
            throw new MerchantEnrichmentException("Merchant profile does not advertise cart capability");
        }
        URI endpoint = shoppingEndpoint(profile)
                .map(value -> urlValidator.validateMerchantUrl(profile.domain(), value))
                .orElseGet(() -> urlValidator.validateMerchantUrl(
                        profile.domain(), "https://" + profile.domain() + "/api/ucp/mcp"));
        return new Observation(profile.profileEndpoint(), endpoint);
    }

    private boolean hasCartCapability(MerchantUcpProfileObservation profile) {
        return profile.capabilities().stream().anyMatch(name -> name.startsWith(CART_CAPABILITY_PREFIX));
    }

    private Optional<String> shoppingEndpoint(MerchantUcpProfileObservation profile) {
        return profile.services().getOrDefault(SHOPPING_SERVICE, List.of()).stream()
                .filter(service -> service != null && "mcp".equalsIgnoreCase(service.transport()))
                .map(UcpServiceDefinition::endpoint)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private String normalizedDomain(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String domain = IDN.toASCII(value.trim()).toLowerCase(Locale.ROOT);
            while (domain.endsWith(".")) {
                domain = domain.substring(0, domain.length() - 1);
            }
            return domain.startsWith("www.") ? domain.substring(4) : domain;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record Observation(URI profileEndpoint, URI endpoint) {
    }
}
