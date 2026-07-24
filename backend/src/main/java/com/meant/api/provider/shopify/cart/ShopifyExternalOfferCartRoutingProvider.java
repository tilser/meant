package com.meant.api.provider.shopify.cart;

import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.cart.service.port.ExternalOfferCartRoutingProvider;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.service.MerchantCartProviderLookupService;
import com.meant.api.module.merchant.service.MerchantExecutionPolicyService;
import com.meant.api.module.merchant.service.MerchantOutboundUrlValidator;
import com.meant.api.module.merchant.service.MerchantUcpProfileObservationService;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.merchant.service.dto.MerchantIdentityResolutionContext;
import com.meant.api.module.merchant.service.dto.MerchantUcpProfileObservation;
import com.meant.api.module.merchant.service.dto.UcpServiceDefinition;
import com.meant.api.module.merchant.service.query.EvaluateObservedProviderPolicyQuery;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import com.meant.api.provider.shopify.identity.ShopifyMerchantIdentityResolver;
import java.net.IDN;
import java.net.URI;
import java.time.Instant;
import java.util.EnumSet;
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
    private static final String CHECKOUT_CAPABILITY_PREFIX = "dev.ucp.shopping.checkout";

    private final ShopifyCartProperties properties;
    private final MerchantCartProviderLookupService merchantLookup;
    private final MerchantUcpProfileObservationService profileObservations;
    private final MerchantOutboundUrlValidator urlValidator;
    private final MerchantExecutionPolicyService executionPolicyService;
    private final ShopifyMerchantIdentityResolver identityResolver;

    public ShopifyExternalOfferCartRoutingProvider(
            ShopifyCartProperties properties,
            MerchantCartProviderLookupService merchantLookup,
            MerchantUcpProfileObservationService profileObservations,
            MerchantOutboundUrlValidator urlValidator,
            MerchantExecutionPolicyService executionPolicyService,
            ShopifyMerchantIdentityResolver identityResolver
    ) {
        this.properties = properties;
        this.merchantLookup = merchantLookup;
        this.profileObservations = profileObservations;
        this.urlValidator = urlValidator;
        this.executionPolicyService = executionPolicyService;
        this.identityResolver = identityResolver;
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
        return route(domain, merchant.value(), false);
    }

    @Override
    public boolean supportsPersisted(CartRoutingTarget target) {
        return target.provider() == MerchantIntegrationProvider.SHOPIFY
                && target.merchantIntegrationId() == null;
    }

    @Override
    public Optional<CartRoutingTarget> restore(CartRoutingTarget target) {
        String domain = normalizedDomain(target.merchantProvider().routingDomain());
        return domain == null || target.externalMerchantId() == null
                ? Optional.empty() : route(domain, target.externalMerchantId(), false);
    }

    @Override
    public Optional<CartRoutingTarget> restoreForCheckout(CartRoutingTarget target) {
        String domain = normalizedDomain(target.merchantProvider().routingDomain());
        return domain == null || target.externalMerchantId() == null
                ? Optional.empty() : route(domain, target.externalMerchantId(), true);
    }

    private Optional<CartRoutingTarget> route(
            String domain,
            String externalMerchantId,
            boolean checkoutPolicyRequired
    ) {
        try {
            Optional<MerchantCartProvider> match = merchantLookup.findActiveByCanonicalDomain(domain)
                    .or(() -> merchantLookup.findActiveByShopifyShopId(externalMerchantId));
            if (match.isPresent()) {
                Optional<Observation> stored = storedObservation(domain, match.get());
                Observation observation = stored.isEmpty()
                        ? refreshObservation(domain, externalMerchantId)
                        : checkoutPolicyRequired
                                && !hasCapability(stored.get().capabilities(), CHECKOUT_CAPABILITY_PREFIX)
                                ? observeObservation(domain, externalMerchantId)
                                : stored.get();
                return Optional.of(externalTarget(domain, externalMerchantId, observation));
            }
            return discovered(domain, externalMerchantId);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public CartRoutingTarget refresh(CartRoutingTarget target) {
        String domain = normalizedDomain(target.merchantProvider().routingDomain());
        if (domain == null || target.externalMerchantId() == null) {
            throw new MerchantEnrichmentException("Stored Shopify cart route is incomplete");
        }
        Observation observation = refreshObservation(domain, target.externalMerchantId());
        return new CartRoutingTarget(target.scopeKey(), target.provider(), null, target.externalMerchantId(),
                externalProvider(domain, observation));
    }

    public CartRoutingTarget refreshForCheckout(CartRoutingTarget target) {
        if (target.merchantIntegrationId() != null) {
            throw new MerchantEnrichmentException("Managed Shopify cart routes cannot refresh as external routes");
        }
        String domain = normalizedDomain(target.merchantProvider().routingDomain());
        if (domain == null || target.externalMerchantId() == null) {
            throw new MerchantEnrichmentException("Stored Shopify checkout route is incomplete");
        }
        Observation observation = refreshObservation(domain, target.externalMerchantId());
        if (!hasCapability(observation.capabilities(), CHECKOUT_CAPABILITY_PREFIX)) {
            throw new MerchantEnrichmentException("Merchant profile does not advertise checkout capability");
        }
        return externalTarget(domain, target.externalMerchantId(), observation);
    }

    private Observation refreshObservation(String domain, String externalMerchantId) {
        URI profileOrigin = profileOrigin(domain);
        return executable(profileObservations.refresh(
                domain,
                profileOrigin,
                MerchantIntegrationProvider.SHOPIFY,
                externalMerchantId
        ), externalMerchantId);
    }

    private Observation observeObservation(String domain, String externalMerchantId) {
        URI profileOrigin = profileOrigin(domain);
        return executable(profileObservations.observe(
                domain,
                profileOrigin,
                MerchantIntegrationProvider.SHOPIFY,
                externalMerchantId
        ), externalMerchantId);
    }

    private Optional<CartRoutingTarget> discovered(String domain, String externalMerchantId) {
        try {
            URI profileOrigin = profileOrigin(domain);
            Observation observation = executable(profileObservations.observe(
                    domain,
                    profileOrigin,
                    MerchantIntegrationProvider.SHOPIFY,
                    externalMerchantId
            ), externalMerchantId);
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
            return Optional.of(new Observation(
                    profileEndpoint,
                    endpoint,
                    provider.merchantDomain(),
                    provider.advertisedCapabilities()
            ));
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
                null, observation.merchantDomain(), domain,
                observation.endpoint().toString(), observation.profileEndpoint().toString(),
                List.of(), externalExecutionPolicy(observation.capabilities()), Instant.now(), observation.capabilities());
    }

    private Observation executable(MerchantUcpProfileObservation profile, String externalMerchantId) {
        if (!hasCartCapability(profile)) {
            throw new MerchantEnrichmentException("Merchant profile does not advertise cart capability");
        }
        String merchantDomain = identityResolver.resolve(new MerchantIdentityResolutionContext(
                        profile.domain(),
                        MerchantIntegrationProvider.SHOPIFY,
                        externalMerchantId,
                        profile.profile()
                ))
                .map(identity -> identity.canonicalDomain())
                .orElseThrow(() -> new MerchantEnrichmentException(
                        "Merchant profile did not expose a verified storefront origin"));
        URI endpoint = shoppingEndpoint(profile)
                .map(value -> urlValidator.validateMerchantUrl(profile.domain(), value))
                .orElseGet(() -> urlValidator.validateMerchantUrl(
                        profile.domain(), "https://" + profile.domain() + "/api/ucp/mcp"));
        return new Observation(
                profile.profileEndpoint(),
                endpoint,
                merchantDomain,
                Set.copyOf(profile.capabilities())
        );
    }

    private boolean hasCartCapability(MerchantUcpProfileObservation profile) {
        return hasCapability(profile.capabilities(), CART_CAPABILITY_PREFIX);
    }

    private boolean hasCapability(Set<String> capabilities, String prefix) {
        return capabilities != null && capabilities.stream().anyMatch(name -> name.startsWith(prefix));
    }

    private MerchantExecutionPolicy externalExecutionPolicy(Set<String> capabilities) {
        EnumSet<MerchantIntegrationRole> roles = EnumSet.noneOf(MerchantIntegrationRole.class);
        if (hasCapability(capabilities, CART_CAPABILITY_PREFIX)) {
            roles.add(MerchantIntegrationRole.CART);
        }
        if (hasCapability(capabilities, CHECKOUT_CAPABILITY_PREFIX)) {
            roles.add(MerchantIntegrationRole.CHECKOUT);
        }
        return executionPolicyService.evaluateObservedProvider(new EvaluateObservedProviderPolicyQuery(
                MerchantIntegrationProvider.SHOPIFY,
                MerchantIntegrationAuthStrategy.OAUTH_BEARER,
                Set.copyOf(roles),
                capabilities
        ));
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

    private record Observation(
            URI profileEndpoint,
            URI endpoint,
            String merchantDomain,
            Set<String> capabilities
    ) {
    }
}
