package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.NotBlank;

public record UserLocationCommand(
        @NotBlank
        String country,

        @NotBlank
        String code,

        @NotBlank
        String city
) {
}
