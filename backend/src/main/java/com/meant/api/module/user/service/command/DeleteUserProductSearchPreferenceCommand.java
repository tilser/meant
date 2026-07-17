package com.meant.api.module.user.service.command;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DeleteUserProductSearchPreferenceCommand(
        @NotNull UUID userId,
        @NotBlank @Size(max = 80) String scope,
        @NotNull UserProductSearchAttributeName attributeName
) {

    @AssertTrue(message = "Only stable size preferences can be deleted")
    public boolean isStableAttribute() {
        return attributeName == null || attributeName == UserProductSearchAttributeName.SIZE;
    }
}
