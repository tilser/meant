package com.meant.api.module.order.service.command;

import com.meant.api.plugin.order.common.dto.UcpOrderResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecordInboundOrderCommand(
        @NotBlank String merchantDomain,
        @NotBlank String source,
        @NotNull UcpOrderResponse.Order order,
        @NotBlank String rawPayload,
        String deliveryId,
        String topic
) {
}
