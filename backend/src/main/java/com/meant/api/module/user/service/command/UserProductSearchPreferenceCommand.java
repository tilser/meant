package com.meant.api.module.user.service.command;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UserProductSearchPreferenceCommand(
        @NotBlank @Size(max = 80) String scope,
        @NotNull UserProductSearchAttributeName attributeName,
        @NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 120) String> values
) {

    @AssertTrue(message = "Only stable size preferences can be saved")
    public boolean isStableAttribute() {
        return attributeName == null || attributeName == UserProductSearchAttributeName.SIZE;
    }
}
