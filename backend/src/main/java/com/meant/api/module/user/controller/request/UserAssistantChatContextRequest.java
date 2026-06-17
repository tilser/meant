package com.meant.api.module.user.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UserAssistantChatContextRequest(
        @Size(max = 40)
        String view,

        @Size(max = 120)
        String contextLabel,

        @Size(max = 500)
        String currentSearchQuery,

        @Size(max = 160)
        String selectedMerchantName,

        Integer savedProductCount,

        Integer cartItemCount,

        @Size(max = 12)
        List<@Valid Product> visibleProducts,

        @Size(max = 12)
        List<@Valid CartItem> cartItems,

        @Size(max = 8)
        List<@Valid Order> orders
) {

    public record Product(
            @Size(max = 160)
            String id,

            @Size(max = 220)
            String name,

            @Size(max = 160)
            String brand,

            @Size(max = 120)
            String category,

            Integer match,

            Double priceFrom,

            @Size(max = 500)
            String note
    ) {
    }

    public record CartItem(
            @Size(max = 220)
            String name,

            @Size(max = 160)
            String merchant,

            Integer quantity,

            Double price
    ) {
    }

    public record Order(
            @Size(max = 80)
            String id,

            @Size(max = 40)
            String date,

            @Size(max = 80)
            String status,

            @Size(max = 240)
            String statusNote,

            Integer itemCount
    ) {
    }
}
