package com.meant.api.module.order.controller;

import com.meant.api.module.order.service.ShopifyOrderWebhookService;
import com.meant.api.module.order.service.command.ReceiveShopifyOrderWebhookCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/shopify/orders")
@RequiredArgsConstructor
@Tag(name = "Order Webhooks", description = "Merchant order webhook receivers")
public class ShopifyOrderWebhookController {

    private final ShopifyOrderWebhookService webhookService;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Receive Shopify order webhook")
    public void receive(
            @RequestHeader("X-Shopify-Shop-Domain") String shopDomain,
            @RequestHeader("X-Shopify-Topic") String topic,
            @RequestHeader(value = "X-Shopify-Webhook-Id", required = false) String webhookId,
            @RequestHeader("X-Shopify-Hmac-Sha256") String hmac,
            @RequestBody byte[] body
    ) {
        webhookService.receive(new ReceiveShopifyOrderWebhookCommand(
                shopDomain,
                topic,
                webhookId,
                hmac,
                body
        ));
    }
}
