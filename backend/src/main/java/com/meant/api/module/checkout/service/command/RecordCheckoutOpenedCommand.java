package com.meant.api.module.checkout.service.command;

import com.meant.api.module.checkout.constant.CheckoutAttributionRail;
import com.meant.api.module.checkout.constant.CheckoutAttributionTrigger;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RecordCheckoutOpenedCommand(
        @NotNull UUID userId,
        @NotNull UUID cartId,
        @NotNull UUID checkoutAttemptId,
        @NotNull CheckoutAttributionRail rail,
        @NotNull CheckoutAttributionTrigger trigger,
        UUID embeddedSessionId
) {
}
