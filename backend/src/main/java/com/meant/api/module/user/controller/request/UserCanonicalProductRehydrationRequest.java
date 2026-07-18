package com.meant.api.module.user.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Server-issued canonical product keys to rehydrate for durable chat history")
public record UserCanonicalProductRehydrationRequest(
        @Schema(
                description = "One to 21 authenticated-user canonical product keys, deduplicated in first-seen order",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotEmpty
        @Size(max = 21)
        List<@NotBlank @Size(max = 200) String> canonicalProductKeys
) {
    public UserCanonicalProductRehydrationRequest {
        canonicalProductKeys = canonicalProductKeys == null ? null : canonicalProductKeys.stream()
                .map(key -> key == null ? null : key.trim())
                .toList();
    }
}
