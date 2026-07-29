package com.meant.api.module.user.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Trusted buyer-visible conversation context, ordered from oldest to newest. */
public record UserProductSearchConversationMessage(
        @NotNull Role role,
        @NotBlank @Size(max = 4_000) String text
) {

    public enum Role {
        USER,
        ASSISTANT
    }
}
