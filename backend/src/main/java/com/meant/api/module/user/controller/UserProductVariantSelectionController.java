package com.meant.api.module.user.controller;

import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.controller.request.SelectUserProductVariantRequest;
import com.meant.api.module.user.controller.response.UserProductVariantSelectionResponse;
import com.meant.api.module.user.service.UserProductVariantSelectionService;
import com.meant.api.module.user.service.command.SelectUserProductVariantCommand;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Users", description = "Profile endpoints for the authenticated Supabase user")
public class UserProductVariantSelectionController {
    private final UserProductVariantSelectionService selectionService;

    @PostMapping("/me/product-variant-selections")
    @Operation(
            operationId = "selectUserProductVariantV1",
            summary = "Select an exact product variant for the current user",
            description = "Uses a server-issued live or durable saved offer only as a trusted product and merchant "
                    + "anchor. The current provider resolves the requested options. A new offer key is returned only "
                    + "for a complete, unrelaxed, unique exact variant; provider and commerce identifiers are never "
                    + "accepted from the browser."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Current detail with an exact selected offer when the combination is unambiguous",
            content = @Content(schema = @Schema(implementation = UserProductVariantSelectionResponse.class))
    )
    public UserProductVariantSelectionResponse select(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SelectUserProductVariantRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        SelectUserProductVariantCommand command = new SelectUserProductVariantCommand(
                authenticatedUser.id(),
                request.anchorOfferKey(),
                request.selectedOptions().stream()
                        .map(option -> new SelectUserProductVariantCommand.SelectedOption(
                                option.name(), option.value()))
                        .toList(),
                request.preferredOptionName()
        );
        return UserProductVariantSelectionResponse.from(selectionService.select(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                command
        ));
    }
}
