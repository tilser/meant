package com.meant.api.module.user.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImportPurchasedInventoryItemsCommand(
        @NotNull
        UUID userId,

        @NotEmpty
        List<@Valid PurchasedItem> items
) {

    public ImportPurchasedInventoryItemsCommand {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record PurchasedItem(
            @NotBlank
            @Size(max = 500)
            String productKey,

            @Size(max = 500)
            String productHash,

            @NotBlank
            @Size(max = 500)
            String name,

            @Size(max = 500)
            String brand,

            @Size(max = 2048)
            String imageUrl,

            @Size(max = 2048)
            String productUrl,

            @NotNull
            @Positive
            Integer quantity,

            Instant purchasedAt
    ) {

        public PurchasedItem {
            quantity = quantity == null ? 1 : quantity;
        }
    }
}
