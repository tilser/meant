package com.meant.api.plugin.payment.common;

import com.meant.api.plugin.payment.common.exception.DuplicatePaymentHandlerException;
import com.meant.api.plugin.payment.common.exception.UnknownPaymentHandlerException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PaymentHandlerRegistry {

    private final List<PaymentHandler<?, ?>> handlers;
    private final Map<String, PaymentHandler<?, ?>> handlersByName;

    public PaymentHandlerRegistry(List<PaymentHandler<?, ?>> handlers) {
        this.handlers = handlers == null ? List.of() : List.copyOf(handlers);
        this.handlersByName = buildHandlerMap(this.handlers);
    }

    public PaymentHandler<?, ?> handler(String name) {
        return findHandler(name)
                .orElseThrow(() -> new UnknownPaymentHandlerException(name));
    }

    public Optional<PaymentHandler<?, ?>> findHandler(String name) {
        return Optional.ofNullable(handlersByName.get(normalizedName(name)));
    }

    public List<PaymentHandler<?, ?>> handlers() {
        return handlers;
    }

    public Map<String, PaymentHandler<?, ?>> handlersByName() {
        return handlersByName;
    }

    private Map<String, PaymentHandler<?, ?>> buildHandlerMap(List<PaymentHandler<?, ?>> handlers) {
        Map<String, PaymentHandler<?, ?>> values = new LinkedHashMap<>();
        for (PaymentHandler<?, ?> handler : handlers) {
            register(values, handler.id(), handler);
            for (String name : handler.names()) {
                register(values, name, handler);
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private void register(Map<String, PaymentHandler<?, ?>> handlers, String name, PaymentHandler<?, ?> handler) {
        String normalizedName = normalizedName(name);
        if (normalizedName.isBlank()) {
            return;
        }
        PaymentHandler<?, ?> existing = handlers.putIfAbsent(normalizedName, handler);
        if (existing != null && existing != handler) {
            throw new DuplicatePaymentHandlerException(normalizedName, existing.id(), handler.id());
        }
    }

    private String normalizedName(String name) {
        return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
