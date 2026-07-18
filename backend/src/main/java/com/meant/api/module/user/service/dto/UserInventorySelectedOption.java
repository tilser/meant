package com.meant.api.module.user.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One typed selected product option retained with a purchased inventory item. */
public record UserInventorySelectedOption(
        @Size(max = 100) String group,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 500) String value
) {
    public UserInventorySelectedOption {
        group = trimToNull(group);
        name = name == null ? null : name.trim();
        value = value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
