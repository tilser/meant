package com.meant.api.module.cart.service.query;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record PartitionSelectedOffersQuery(
        @NotNull UUID userId,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid Item> items
) {

    public record Item(
            @NotBlank @Size(max = 200) String offerKey,
            @Positive Integer quantity
    ) {
    }
}
