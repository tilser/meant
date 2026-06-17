package com.meant.api.module.user.service.dto;

import java.util.List;

public record UserAssistantPageContext(
        String view,
        String contextLabel,
        String currentSearchQuery,
        String selectedMerchantName,
        Integer savedProductCount,
        Integer cartItemCount,
        List<Product> visibleProducts,
        List<CartItem> cartItems,
        List<Order> orders
) {

    public UserAssistantPageContext {
        visibleProducts = visibleProducts == null ? List.of() : List.copyOf(visibleProducts);
        cartItems = cartItems == null ? List.of() : List.copyOf(cartItems);
        orders = orders == null ? List.of() : List.copyOf(orders);
    }

    public record Product(
            String id,
            String name,
            String brand,
            String category,
            Integer match,
            Double priceFrom,
            String note
    ) {
    }

    public record CartItem(
            String name,
            String merchant,
            Integer quantity,
            Double price
    ) {
    }

    public record Order(
            String id,
            String date,
            String status,
            String statusNote,
            Integer itemCount
    ) {
    }
}
