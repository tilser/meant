package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record TransferUserCanonicalProductAccessCommand(
        @NotNull UUID sourceUserId,
        @NotNull UUID targetUserId,
        @NotNull List<@NotBlank String> canonicalProductKeys
) {
    public TransferUserCanonicalProductAccessCommand {
        canonicalProductKeys = canonicalProductKeys == null ? null : List.copyOf(canonicalProductKeys);
    }
}
