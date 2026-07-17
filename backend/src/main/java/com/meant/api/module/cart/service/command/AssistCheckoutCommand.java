package com.meant.api.module.cart.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AssistCheckoutCommand(
        @NotNull
        UUID cartId,
        @NotNull
        UUID userId,
        @NotBlank
        @Size(max = 2000)
        String message,
        @Size(max = 1000)
        String merchantDeliveryHint,
        @Size(max = 40)
        List<@Valid HistoryMessage> history,
        String buyerIp
) {

    public AssistCheckoutCommand {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public AssistCheckoutCommand(
            UUID cartId,
            UUID userId,
            String message,
            String merchantDeliveryHint,
            List<HistoryMessage> history
    ) {
        this(cartId, userId, message, merchantDeliveryHint, history, null);
    }

    public record HistoryMessage(
            @NotBlank
            @Pattern(regexp = "user|assistant")
            String role,
            @NotBlank
            @Size(max = 4000)
            String content
    ) {
    }
}
