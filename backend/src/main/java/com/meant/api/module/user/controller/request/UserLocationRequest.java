package com.meant.api.module.user.controller.request;

import jakarta.validation.constraints.NotBlank;

public record UserLocationRequest(
        @NotBlank
        String country,

        @NotBlank
        String code,

        @NotBlank
        String city
) {
}
