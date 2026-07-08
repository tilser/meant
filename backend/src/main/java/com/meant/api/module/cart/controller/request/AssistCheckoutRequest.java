package com.meant.api.module.cart.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Buyer message for the conversational checkout assistant.")
public record AssistCheckoutRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 2000)
        String message,
        @Schema(
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                description = "Known merchant delivery coverage text to help the assistant suggest a retry destination."
        )
        @Size(max = 1000)
        String merchantDeliveryHint,
        @Schema(
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                description = "Prior conversation turns, oldest first."
        )
        @Size(max = 40)
        List<@Valid HistoryMessageRequest> history
) {

    @Schema(description = "One prior conversation turn.")
    public record HistoryMessageRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"user", "assistant"})
            @NotBlank
            @Pattern(regexp = "user|assistant")
            String role,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            @Size(max = 4000)
            String content
    ) {
    }
}
