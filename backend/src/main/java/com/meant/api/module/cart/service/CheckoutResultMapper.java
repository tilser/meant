package com.meant.api.module.cart.service;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.module.checkout.constant.CheckoutLifecycleState;
import com.meant.api.plugin.support.UcpMoney;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CheckoutResultMapper {

    private final ObjectMapper objectMapper;
    private final CheckoutExecutionPlanner checkoutExecutionPlanner;

    public CheckoutResult from(Cart cart, MerchantExecutionPolicy policy) {
        return from(cart, parseStoredResponse(cart.getRawCheckoutResponse()), policy);
    }

    public CheckoutResult from(
            Cart cart, UcpCheckoutResponse response, MerchantExecutionPolicy policy) {
        UcpCheckoutResponse.Checkout checkout = response == null ? null : response.resolvedCheckout();
        UcpMoney total = checkout == null ? null : checkout.resolvedTotal();
        String currency = firstText(
                total == null ? null : total.currency(),
                checkout == null ? null : checkout.resolvedCurrency(cart.getCurrency()),
                cart.getCurrency()
        );
        String continueUrl = firstText(checkout == null ? null : checkout.continueUrl(), cart.getContinueUrl());
        String checkoutUrl = firstText(checkout == null ? null : checkout.checkoutUrl(), cart.getCheckoutUrl());
        List<CheckoutResult.Message> checkoutMessages = messages(response);
        String lifecycle = response == null
                ? firstText(cart.getCheckoutLifecycleState(), cart.getCheckoutStatus())
                : CheckoutLifecycleState.from(response).name();
        var execution = checkoutExecutionPlanner.resolve(
                lifecycle,
                checkoutMessages,
                policy
        );
        return new CheckoutResult(
                cart.getId(),
                cart.getRemoteCartId(),
                firstText(checkout == null ? null : checkout.id(), cart.getCheckoutId()),
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

    private List<CheckoutResult.Message> messages(UcpCheckoutResponse response) {
        if (response == null) {
            return List.of();
        }
        List<CheckoutResult.Message> messages = new ArrayList<>();
        addMessages(messages, response.messages());
        addErrors(messages, response.errors());
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        if (checkout != null) {
            addMessages(messages, checkout.messages());
        }
        return messages;
    }

    private void addMessages(
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
                        message.message(),
                        message.target()
                ))
                .forEach(target::add);
    }

    private void addErrors(
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
                        error.message(),
                        null
                ))
                .forEach(target::add);
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
