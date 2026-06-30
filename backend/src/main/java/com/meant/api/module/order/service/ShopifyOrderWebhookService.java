package com.meant.api.module.order.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.order.exception.OrderException;
import com.meant.api.module.order.service.command.ReceiveShopifyOrderWebhookCommand;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.repository.UserRepository;
import com.meant.api.plugin.order.common.dto.UcpOrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor
public class ShopifyOrderWebhookService {

    private final ShopifyOrderWebhookVerifier webhookVerifier;
    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final OrderPersistenceService orderPersistenceService;
    private final ObjectMapper objectMapper;

    public void receive(@NotNull @Valid ReceiveShopifyOrderWebhookCommand command) {
        if (!webhookVerifier.verify(command.body(), command.hmac())) {
            throw OrderException.forbidden("Shopify order webhook signature verification failed");
        }
        UcpOrderResponse.Order order = parseOrder(command.body());
        Merchant merchant = merchantRepository.findByDomain(command.shopDomain())
                .orElseThrow(() -> OrderException.notFound("Merchant not found for Shopify shop: " + command.shopDomain()));
        orderPersistenceService.saveSnapshot(
                userId(order),
                merchant,
                "shopify:webhook",
                order,
                new String(command.body(), StandardCharsets.UTF_8),
                command.webhookId(),
                command.topic()
        );
    }

    private UcpOrderResponse.Order parseOrder(byte[] body) {
        try {
            UcpOrderResponse.Order order = objectMapper.readValue(body, UcpOrderResponse.Order.class);
            if (hasText(order.id()) || hasText(order.name()) || hasText(order.orderNumber())) {
                return order;
            }
            UcpOrderResponse response = objectMapper.readValue(body, UcpOrderResponse.class);
            if (response.order() != null) {
                return response.order();
            }
            throw new OrderException("Shopify order webhook did not contain an order");
        } catch (JacksonException exception) {
            throw new OrderException("Shopify order webhook could not be parsed", exception);
        }
    }

    private UUID userId(UcpOrderResponse.Order order) {
        String email = firstText(order.email(), order.customer() == null ? null : order.customer().email());
        if (!hasText(email)) {
            return null;
        }
        String normalizedEmail = email.trim();
        return userRepository.findByEmail(normalizedEmail.toLowerCase())
                .or(() -> userRepository.findByEmail(normalizedEmail))
                .map(User::getId)
                .orElse(null);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
