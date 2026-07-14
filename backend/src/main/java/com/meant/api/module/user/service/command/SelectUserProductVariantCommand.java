package com.meant.api.module.user.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record SelectUserProductVariantCommand(
        @NotNull
        UUID userId,
        @NotBlank
        @Size(max = 200)
        String anchorOfferKey,
        @NotNull
        @Size(max = 20)
        List<@Valid SelectedOption> selectedOptions,
        @Size(max = 200)
        String preferredOptionName
) {
    public SelectUserProductVariantCommand {
        anchorOfferKey = anchorOfferKey == null ? null : anchorOfferKey.trim();
        selectedOptions = selectedOptions == null ? null : List.copyOf(selectedOptions);
        preferredOptionName = preferredOptionName == null || preferredOptionName.isBlank()
                ? null
                : preferredOptionName.trim();
    }

    public record SelectedOption(
            @NotBlank
            @Size(max = 200)
            String name,
            @NotBlank
            @Size(max = 500)
            String value
    ) {
        public SelectedOption {
            name = name == null ? null : name.trim();
            value = value == null ? null : value.trim();
        }
    }
}
