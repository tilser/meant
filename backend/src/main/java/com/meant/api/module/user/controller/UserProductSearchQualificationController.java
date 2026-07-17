package com.meant.api.module.user.controller;

import com.meant.api.module.user.controller.mapper.UserCommandMapper;
import com.meant.api.module.user.controller.request.UserProductSearchQualificationRequest;
import com.meant.api.module.user.controller.response.UserProductSearchQualificationResponse;
import com.meant.api.module.user.service.UserProductSearchQualificationService;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
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
public class UserProductSearchQualificationController {

    private final UserProductSearchQualificationService qualificationService;

    @PostMapping("/me/product-search-qualifications")
    @Operation(
            operationId = "qualifyProductSearchV1",
            summary = "Qualify a product search before catalog discovery",
            description = "Uses the current turn, prior qualification state, and durable user context to decide "
                    + "which supported hard filters need values. Catalog discovery is authorized only when READY."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Current product-search qualification state",
            content = @Content(schema = @Schema(implementation = UserProductSearchQualificationResponse.class))
    )
    public UserProductSearchQualificationResponse qualify(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserProductSearchQualificationRequest request
    ) {
        AuthenticatedUser authenticatedUser = AuthenticatedUser.fromJwt(jwt);
        return UserProductSearchQualificationResponse.from(qualificationService.qualify(
                UserCommandMapper.toEnsureProfileCommand(authenticatedUser),
                new QualifyUserProductSearchCommand(
                        authenticatedUser.id(),
                        request.conversationId(),
                        request.qualificationId(),
                        request.message(),
                        request.merchantId()
                )
        ));
    }
}
