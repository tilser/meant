package com.meant.api.module.cart.service;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
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

    public CheckoutResult from(Cart cart, boolean nativeCheckoutEnabled) {
        UcpCheckoutResponse response = parseResponse(cart.getRawCheckoutResponse());
        UcpCheckoutResponse.Checkout checkout = response == null ? null : response.resolvedCheckout();
        UcpMoney total = checkout == null ? null : checkout.resolvedTotal();
        String currency = firstText(
                total == null ? null : total.currency(),
                checkout == null ? null : checkout.resolvedCurrency(cart.getCurrency()),
                cart.getCurrency()
        );
        return new CheckoutResult(
                cart.getId(),
                cart.getRemoteCartId(),
                firstText(checkout == null ? null : checkout.id(), cart.getCheckoutId()),
                firstText(checkout == null ? null : checkout.status(), cart.getCheckoutStatus()),
                firstText(checkout == null ? null : checkout.checkoutUrl(), cart.getCheckoutUrl()),
                firstText(checkout == null ? null : checkout.continueUrl(), cart.getContinueUrl()),
                response == null ? null : response.version(),
                total == null ? null : total.amount(),
                currency == null ? null : currency.toUpperCase(Locale.ROOT),
                messages(response),
                nativeCheckoutEnabled
        );
    }

    private UcpCheckoutResponse parseResponse(String rawCheckoutResponse) {
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

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
