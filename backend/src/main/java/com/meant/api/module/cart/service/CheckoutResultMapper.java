package com.meant.api.module.cart.service;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.checkout.constant.CheckoutLifecycleState;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.support.UcpMoney;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutResultMapper {

    private final ObjectMapper objectMapper;
    private final CheckoutExecutionPlanner checkoutExecutionPlanner;

    public CheckoutResult from(Cart cart, MerchantExecutionPolicy policy) {
        return from(cart, parseStoredResponse(cart.getRawCheckoutResponse()), policy, null);
    }

    public CheckoutResult from(Cart cart, MerchantCartProvider provider) {
        return from(
                cart,
                parseStoredResponse(cart.getRawCheckoutResponse()),
                provider.executionPolicy(),
                provider
        );
    }

    public CheckoutResult from(
            Cart cart, UcpCheckoutResponse response, MerchantExecutionPolicy policy) {
        return from(cart, response, policy, null);
    }

    public CheckoutResult from(
            Cart cart,
            UcpCheckoutResponse response,
            MerchantCartProvider provider
    ) {
        return from(cart, response, provider.executionPolicy(), provider);
    }

    private CheckoutResult from(
            Cart cart,
            UcpCheckoutResponse response,
            MerchantExecutionPolicy policy,
            MerchantCartProvider provider
    ) {
        UcpCheckoutResponse.Checkout checkout = response == null ? null : response.resolvedCheckout();
        UcpMoney total = checkout == null ? null : checkout.resolvedTotal();
        String currency = firstText(
                total == null ? null : total.currency(),
                checkout == null ? null : checkout.resolvedCurrency(cart.getCurrency()),
                cart.getCurrency()
        );
        String continueUrl = BuyerSafeCheckoutUrl.firstSafe(
                cart,
                provider,
                checkout == null ? null : checkout.continueUrl(),
                cart.getContinueUrl()
        );
        String checkoutUrl = BuyerSafeCheckoutUrl.firstSafe(
                cart,
                provider,
                checkout == null ? null : checkout.checkoutUrl(),
                cart.getCheckoutUrl()
        );
        List<CheckoutResult.Message> checkoutMessages = messages(cart, response, provider);
        String lifecycle = response == null
                ? firstText(cart.getCheckoutLifecycleState(), cart.getCheckoutStatus())
                : CheckoutLifecycleState.from(response).name();
        var execution = checkoutExecutionPlanner.resolve(
                lifecycle,
                checkoutMessages,
                policy
        );
        log.info(
                "Checkout execution decision lifecycle={} responseStatus={} nextAction={} selectedRail={} "
                        + "embeddedAvailable={} messageCodes={} ineligibilityReasons={}",
                lifecycle,
                checkout == null ? null : checkout.status(),
                execution.nextAction(),
                execution.selectedRail(),
                policy != null && policy.decision(CommerceOperation.EMBEDDED_CHECKOUT).available(),
                checkoutMessages.stream().map(CheckoutResult.Message::code)
                        .filter(StringUtils::hasText).distinct().limit(10).toList(),
                execution.ineligibilityReasons()
        );
        return new CheckoutResult(
                cart.getId(),
                cart.getRemoteCartId(),
                firstText(checkout == null ? null : checkout.id(), cart.getCheckoutId()),
                cart.getCheckoutAttemptId(),
                firstText(checkout == null ? null : checkout.status(), cart.getCheckoutStatus()),
                checkoutUrl,
                continueUrl,
                firstText(response == null ? null : response.version(), cart.getCheckoutProtocolVersion()),
                total == null ? null : total.amount(),
                currency == null ? null : currency.toUpperCase(Locale.ROOT),
                checkoutMessages,
                execution.nextAction(),
                execution.selectedRail(),
                execution.ineligibilityReasons(),
                policy,
                embeddedConfiguration(response)
        );
    }

    private com.meant.api.module.cart.service.dto.EmbeddedCheckoutConfiguration embeddedConfiguration(
            UcpCheckoutResponse response) {
        if (response == null || response.ucp() == null) {
            return null;
        }
        return response.ucp().services().getOrDefault("dev.ucp.shopping", List.of()).stream()
                .filter(binding -> binding != null && "embedded".equalsIgnoreCase(binding.transport()))
                .findFirst()
                .map(binding -> new com.meant.api.module.cart.service.dto.EmbeddedCheckoutConfiguration(
                        firstText(binding.version(), response.version()),
                        binding.config() == null ? List.of() : binding.config().delegate(),
                        binding.config() == null || binding.config().auth() == null
                                ? null : binding.config().auth().type()))
                .orElse(null);
    }

    UcpCheckoutResponse parseStoredResponse(String rawCheckoutResponse) {
        if (!StringUtils.hasText(rawCheckoutResponse)) {
            return null;
        }
        try {
            return objectMapper.readValue(rawCheckoutResponse, UcpCheckoutResponse.class);
        } catch (JacksonException exception) {
            return null;
        }
    }

    private List<CheckoutResult.Message> messages(
            Cart cart,
            UcpCheckoutResponse response,
            MerchantCartProvider provider
    ) {
        if (response == null) {
            return List.of();
        }
        List<CheckoutResult.Message> messages = new ArrayList<>();
        addMessages(cart, provider, messages, response.messages());
        addErrors(cart, provider, messages, response.errors());
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        if (checkout != null) {
            addMessages(cart, provider, messages, checkout.messages());
        }
        return messages;
    }

    private void addMessages(
            Cart cart,
            MerchantCartProvider provider,
            List<CheckoutResult.Message> target,
            List<UcpCheckoutResponse.CheckoutMessage> messages
    ) {
        if (messages == null) {
            return;
        }
        messages.stream()
                .filter(message -> message != null && StringUtils.hasText(message.message()))
                .map(message -> new CheckoutResult.Message(
                        message.type(),
                        message.code(),
                        message.severity(),
                        sanitize(cart, provider, message.message()),
                        sanitize(cart, provider, message.target())
                ))
                .forEach(target::add);
    }

    private void addErrors(
            Cart cart,
            MerchantCartProvider provider,
            List<CheckoutResult.Message> target,
            List<UcpCheckoutResponse.CheckoutError> errors
    ) {
        if (errors == null) {
            return;
        }
        errors.stream()
                .filter(error -> error != null && StringUtils.hasText(error.message()))
                .map(error -> new CheckoutResult.Message(
                        "error",
                        error.code(),
                        error.isRecoverable() ? "recoverable" : "unrecoverable",
                        sanitize(cart, provider, error.message()),
                        null
                ))
                .forEach(target::add);
    }

    private String sanitize(Cart cart, MerchantCartProvider provider, String value) {
        String providerSafe = MerchantBuyerTextSanitizer.sanitize(value, provider);
        return MerchantBuyerTextSanitizer.sanitize(
                providerSafe,
                cart.getMerchantDomain(),
                cart.getRoutingDomain(),
                cart.getEndpoint()
        );
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
