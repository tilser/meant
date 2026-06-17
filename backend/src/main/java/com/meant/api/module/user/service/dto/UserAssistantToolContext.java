package com.meant.api.module.user.service.dto;

import java.util.List;

public record UserAssistantToolContext(
        List<UserSavedProductResult> savedProducts
) {

    public UserAssistantToolContext {
        savedProducts = savedProducts == null ? List.of() : List.copyOf(savedProducts);
    }

    public static UserAssistantToolContext empty() {
        return new UserAssistantToolContext(List.of());
    }

    public boolean hasSavedProducts() {
        return !savedProducts.isEmpty();
    }
}
