package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.CapabilityAuthorizationStatus;
import com.meant.api.module.merchant.constant.CapabilityIntegrationHealth;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantIntegration;
import com.meant.api.module.merchant.properties.MerchantExecutionPolicyProperties;
import com.meant.api.module.merchant.service.dto.CapabilityAuthorizationDecision;
import com.meant.api.module.merchant.service.dto.CapabilityEvaluationInput;
import com.meant.api.module.merchant.service.dto.CapabilityFallback;
import com.meant.api.module.merchant.service.dto.CommerceCapabilityDecision;
import com.meant.api.module.merchant.service.dto.MerchantCapabilityReadinessContext;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.merchant.service.query.EvaluateObservedProviderPolicyQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Slf4j
@Validated
public class MerchantExecutionPolicyService {

    private static final String GENERIC_UCP_CART_CAPABILITY = "dev.ucp.shopping.cart";

    private final MerchantExecutionPolicyProperties properties;
    private final CapabilityExecutionPolicyEvaluator evaluator;
    private final Map<MerchantIntegrationProvider, MerchantCapabilityReadinessAdapter> adapters;

    public MerchantExecutionPolicyService(
            MerchantExecutionPolicyProperties properties,
            CapabilityExecutionPolicyEvaluator evaluator,
            List<MerchantCapabilityReadinessAdapter> adapters
    ) {
        this.properties = properties;
        this.evaluator = evaluator;
        this.adapters = indexAdapters(adapters);
    }

    public MerchantExecutionPolicy evaluate(
            Merchant merchant,
            List<MerchantIntegration> integrations,
            Set<String> advertisedCapabilities
    ) {
        List<MerchantIntegration> availableIntegrations = integrations == null ? List.of() : List.copyOf(integrations);
        Set<String> capabilities = advertisedCapabilities == null ? Set.of() : Set.copyOf(advertisedCapabilities);
        Map<CommerceOperation, CommerceCapabilityDecision> checkoutFamily = evaluateCheckoutFamily(
                merchant,
                availableIntegrations,
                capabilities
        );
        List<CommerceCapabilityDecision> decisions = Arrays.stream(CommerceOperation.values())
                .map(operation -> checkoutFamily.containsKey(operation)
                        ? checkoutFamily.get(operation)
                        : evaluateOperation(merchant, availableIntegrations, capabilities, operation))
                .toList();
        decisions.forEach(decision -> log.debug(
                "Commerce capability decision merchantId={} operation={} provider={} integrationId={} "
                        + "availability={} selectedRail={} reasons={}",
                merchant.getId(),
                decision.operation(),
                decision.provider(),
                decision.integrationId(),
                decision.availability(),
                decision.selectedRail(),
                decision.ineligibilityReasons()
        ));
        return new MerchantExecutionPolicy(decisions);
    }

    public MerchantExecutionPolicy evaluateObservedProvider(
            @NotNull @Valid EvaluateObservedProviderPolicyQuery query
    ) {
        MerchantCapabilityReadinessAdapter adapter = adapters.get(query.provider());
        MerchantCapabilityReadinessContext context = new MerchantCapabilityReadinessContext(
                query.provider(), query.authStrategy(), query.roles(), query.advertisedCapabilities());
        List<CommerceCapabilityDecision> decisions = Arrays.stream(CommerceOperation.values())
                .map(operation -> evaluateObservedProvider(adapter, context, operation))
                .toList();
        return new MerchantExecutionPolicy(decisions);
    }

    private CommerceCapabilityDecision evaluateObservedProvider(
            MerchantCapabilityReadinessAdapter adapter,
            MerchantCapabilityReadinessContext context,
            CommerceOperation operation
    ) {
        boolean roleSupported = supportsRole(context.roles(), operation);
        boolean advertised = roleSupported && adapter != null && adapter.advertised(operation, context);
        CapabilityAuthorizationDecision authorization = adapter == null
                ? CapabilityAuthorizationDecision.unavailable(CapabilityAuthorizationStatus.UNSUPPORTED)
                : adapter.authorization(operation, context);
        return evaluator.evaluate(new CapabilityEvaluationInput(
                operation,
                advertised,
                authorization,
                properties.rolloutEnabled(operation, false),
                CapabilityIntegrationHealth.HEALTHY,
                fallback(operation),
                executionRail(operation),
                null,
                context.provider()
        ));
    }

    private Map<CommerceOperation, CommerceCapabilityDecision> evaluateCheckoutFamily(
            Merchant merchant,
            List<MerchantIntegration> integrations,
            Set<String> advertisedCapabilities
    ) {
        CheckoutCapabilityFamily selected = integrations.stream()
                .filter(integration -> supportsRole(
                        effectiveRoles(merchant, integration, advertisedCapabilities),
                        CommerceOperation.CHECKOUT_SESSION))
                .map(integration -> new CheckoutCapabilityFamily(
                        evaluateIntegration(
                                merchant,
                                integration,
                                advertisedCapabilities,
                                CommerceOperation.CHECKOUT_SESSION
                        ),
                        evaluateIntegration(
                                merchant,
                                integration,
                                advertisedCapabilities,
                                CommerceOperation.EMBEDDED_CHECKOUT
                        ),
                        evaluateIntegration(
                                merchant,
                                integration,
                                advertisedCapabilities,
                                CommerceOperation.DIRECT_CHECKOUT_COMPLETION
                        )
                ))
                .min(Comparator
                        .comparingInt(this::checkoutFamilyRank)
                        .thenComparingInt(family -> family.session().ineligibilityReasons().size()))
                .orElse(null);
        Map<CommerceOperation, CommerceCapabilityDecision> decisions = new EnumMap<>(CommerceOperation.class);
        if (selected == null) {
            decisions.put(
                    CommerceOperation.CHECKOUT_SESSION,
                    evaluateWithoutIntegration(merchant, CommerceOperation.CHECKOUT_SESSION)
            );
            decisions.put(
                    CommerceOperation.EMBEDDED_CHECKOUT,
                    evaluateWithoutIntegration(merchant, CommerceOperation.EMBEDDED_CHECKOUT)
            );
            decisions.put(
                    CommerceOperation.DIRECT_CHECKOUT_COMPLETION,
                    evaluateWithoutIntegration(merchant, CommerceOperation.DIRECT_CHECKOUT_COMPLETION)
            );
        } else {
            decisions.put(CommerceOperation.CHECKOUT_SESSION, selected.session());
            decisions.put(CommerceOperation.EMBEDDED_CHECKOUT, selected.embedded());
            decisions.put(CommerceOperation.DIRECT_CHECKOUT_COMPLETION, selected.direct());
        }
        return Map.copyOf(decisions);
    }

    private CommerceCapabilityDecision evaluateOperation(
            Merchant merchant,
            List<MerchantIntegration> integrations,
            Set<String> advertisedCapabilities,
            CommerceOperation operation
    ) {
        List<CommerceCapabilityDecision> candidates = integrations.stream()
                .filter(integration -> supportsRole(
                        effectiveRoles(merchant, integration, advertisedCapabilities),
                        operation))
                .map(integration -> evaluateIntegration(
                        merchant,
                        integration,
                        advertisedCapabilities,
                        operation
                ))
                .toList();
        return candidates.stream()
                .min(Comparator
                        .comparingInt(this::availabilityRank)
                        .thenComparingInt(decision -> decision.ineligibilityReasons().size()))
                .orElseGet(() -> evaluateWithoutIntegration(merchant, operation));
    }

    private CommerceCapabilityDecision evaluateIntegration(
            Merchant merchant,
            MerchantIntegration integration,
            Set<String> advertisedCapabilities,
            CommerceOperation operation
    ) {
        MerchantCapabilityReadinessAdapter adapter = adapters.get(integration.getProvider());
        Set<MerchantIntegrationRole> roles = effectiveRoles(merchant, integration, advertisedCapabilities);
        MerchantCapabilityReadinessContext context = new MerchantCapabilityReadinessContext(
                integration.getProvider(),
                integration.getAuthStrategy(),
                roles,
                advertisedCapabilities
        );
        boolean advertised = adapter != null && adapter.advertised(operation, context);
        CapabilityAuthorizationDecision authorization = adapter == null
                ? CapabilityAuthorizationDecision.unavailable(CapabilityAuthorizationStatus.UNSUPPORTED)
                : adapter.authorization(operation, context);
        CommerceCapabilityDecision decision = evaluator.evaluate(new CapabilityEvaluationInput(
                operation,
                advertised,
                authorization,
                properties.rolloutEnabled(operation, merchant.isNativeCheckoutEnabled()),
                CapabilityIntegrationHealth.from(integration.getStatus()),
                fallback(operation),
                executionRail(operation),
                integration.getId(),
                integration.getProvider()
        ));
        log.debug(
                "Commerce capability candidate merchantId={} operation={} provider={} integrationId={} "
                        + "availability={} selectedRail={} reasons={}",
                merchant.getId(),
                operation,
                integration.getProvider(),
                integration.getId(),
                decision.availability(),
                decision.selectedRail(),
                decision.ineligibilityReasons()
        );
        return decision;
    }

    private CommerceCapabilityDecision evaluateWithoutIntegration(Merchant merchant, CommerceOperation operation) {
        return evaluator.evaluate(new CapabilityEvaluationInput(
                operation,
                false,
                CapabilityAuthorizationDecision.unavailable(CapabilityAuthorizationStatus.UNSUPPORTED),
                properties.rolloutEnabled(operation, merchant.isNativeCheckoutEnabled()),
                CapabilityIntegrationHealth.NO_INTEGRATION,
                fallback(operation),
                executionRail(operation),
                null,
                null
        ));
    }

    private Set<MerchantIntegrationRole> effectiveRoles(
            Merchant merchant,
            MerchantIntegration integration,
            Set<String> advertisedCapabilities
    ) {
        Set<MerchantIntegrationRole> roles = EnumSet.noneOf(MerchantIntegrationRole.class);
        if (integration.getRoles() != null) {
            roles.addAll(integration.getRoles());
        }
        if (integration.getProvider() == MerchantIntegrationProvider.GENERIC_UCP
                && sameEndpoint(integration.getEndpoint(), merchant.getAdvertisedMcpEndpoint())
                && advertisedCapabilities.contains(GENERIC_UCP_CART_CAPABILITY)) {
            roles.add(MerchantIntegrationRole.CART);
        }
        return Set.copyOf(roles);
    }

    private boolean sameEndpoint(String integrationEndpoint, String advertisedEndpoint) {
        return integrationEndpoint != null
                && advertisedEndpoint != null
                && integrationEndpoint.trim().equals(advertisedEndpoint.trim());
    }

    private boolean supportsRole(Set<MerchantIntegrationRole> roles, CommerceOperation operation) {
        return switch (operation) {
            case CATALOG -> roles.contains(MerchantIntegrationRole.CATALOG_PROVENANCE)
                    || roles.contains(MerchantIntegrationRole.STOREFRONT_CATALOG);
            case CART -> roles.contains(MerchantIntegrationRole.CART);
            case CHECKOUT_SESSION, EMBEDDED_CHECKOUT, DIRECT_CHECKOUT_COMPLETION ->
                    roles.contains(MerchantIntegrationRole.CHECKOUT);
            case ORDER_READS, ORDER_WEBHOOKS -> roles.contains(MerchantIntegrationRole.ORDERS);
        };
    }

    private CapabilityFallback fallback(CommerceOperation operation) {
        return switch (operation) {
            case EMBEDDED_CHECKOUT, DIRECT_CHECKOUT_COMPLETION ->
                    CapabilityFallback.supported(CommerceExecutionRail.MERCHANT_HANDOFF);
            default -> CapabilityFallback.unsupported();
        };
    }

    private CommerceExecutionRail executionRail(CommerceOperation operation) {
        return switch (operation) {
            case CATALOG -> CommerceExecutionRail.PROVIDER_CATALOG;
            case CART -> CommerceExecutionRail.PROVIDER_CART;
            case CHECKOUT_SESSION -> CommerceExecutionRail.PROVIDER_CHECKOUT_SESSION;
            case EMBEDDED_CHECKOUT -> CommerceExecutionRail.EMBEDDED_CHECKOUT;
            case DIRECT_CHECKOUT_COMPLETION -> CommerceExecutionRail.DIRECT_CHECKOUT_COMPLETION;
            case ORDER_READS -> CommerceExecutionRail.PROVIDER_ORDER_API;
            case ORDER_WEBHOOKS -> CommerceExecutionRail.PROVIDER_ORDER_WEBHOOK;
        };
    }

    private int availabilityRank(CommerceCapabilityDecision decision) {
        return switch (decision.availability()) {
            case AVAILABLE -> 0;
            case FALLBACK_AVAILABLE -> 1;
            case UNAVAILABLE -> 2;
        };
    }

    private int checkoutFamilyRank(CheckoutCapabilityFamily family) {
        if (family.embedded().available()) {
            return 0;
        }
        if (family.direct().available()) {
            return 1;
        }
        if (family.session().available()) {
            return 2;
        }
        return 3;
    }

    private Map<MerchantIntegrationProvider, MerchantCapabilityReadinessAdapter> indexAdapters(
            List<MerchantCapabilityReadinessAdapter> values
    ) {
        Map<MerchantIntegrationProvider, MerchantCapabilityReadinessAdapter> indexed =
                new EnumMap<>(MerchantIntegrationProvider.class);
        if (values != null) {
            values.forEach(adapter -> indexed.put(adapter.provider(), adapter));
        }
        return Map.copyOf(indexed);
    }

    private record CheckoutCapabilityFamily(
            CommerceCapabilityDecision session,
            CommerceCapabilityDecision embedded,
            CommerceCapabilityDecision direct
    ) {
    }
}
