package com.meant.api.module.user.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserProductSearchRequest(
        @NotBlank
        @Size(max = 500)
        String query
) {
}
