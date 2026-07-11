package com.meant.api.module.order.service;

import com.meant.api.module.merchant.service.MerchantOrderSourceLookupService;
import com.meant.api.module.merchant.service.dto.MerchantOrderSourceResult;
import com.meant.api.module.order.exception.OrderException;
import com.meant.api.module.order.service.command.RecordInboundOrderCommand;
import com.meant.api.module.user.service.UserEmailLookupService;
import com.meant.api.plugin.order.common.dto.UcpOrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class InboundOrderService {

    private final MerchantOrderSourceLookupService merchantOrderSourceLookupService;
    private final UserEmailLookupService userEmailLookupService;
    private final OrderPersistenceService orderPersistenceService;

    public void record(@NotNull @Valid RecordInboundOrderCommand command) {
        MerchantOrderSourceResult merchant = merchantOrderSourceLookupService.findByDomain(command.merchantDomain())
                .orElseThrow(() -> OrderException.notFound(
                        "Merchant not found for order source domain: " + command.merchantDomain()
                ));
        orderPersistenceService.saveSnapshot(
                userId(command.order()),
                merchant,
                command.source(),
                command.order(),
                command.rawPayload(),
                command.deliveryId(),
                command.topic()
        );
    }

    private UUID userId(UcpOrderResponse.Order order) {
        String email = firstText(order.email(), order.customer() == null ? null : order.customer().email());
        if (!hasText(email)) {
            return null;
        }
        return userEmailLookupService.findUserId(email).orElse(null);
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
