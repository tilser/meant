package com.meant.api.provider.shopify.order;

import com.meant.api.module.order.exception.OrderException;
import com.meant.api.module.order.service.InboundOrderService;
import com.meant.api.module.order.service.command.RecordInboundOrderCommand;
import com.meant.api.plugin.order.common.dto.UcpOrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
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
    private final InboundOrderService inboundOrderService;
    private final ObjectMapper objectMapper;

    public void receive(@NotNull @Valid ReceiveShopifyOrderWebhookCommand command) {
        if (!webhookVerifier.verify(command.body(), command.hmac())) {
            throw OrderException.forbidden("Shopify order webhook signature verification failed");
        }
        UcpOrderResponse.Order order = parseOrder(command.body());
        inboundOrderService.record(new RecordInboundOrderCommand(
                command.shopDomain(),
                "shopify:webhook",
                order,
                new String(command.body(), StandardCharsets.UTF_8),
                command.webhookId(),
                command.topic()
        ));
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
