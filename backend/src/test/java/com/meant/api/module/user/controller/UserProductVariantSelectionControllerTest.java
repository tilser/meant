package com.meant.api.module.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.user.controller.request.SelectUserProductVariantRequest;
import com.meant.api.module.user.service.UserProductVariantSelectionService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SelectUserProductVariantCommand;
import com.meant.api.module.user.service.dto.UserProductVariantSelectionResult;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;

class UserProductVariantSelectionControllerTest {
    private static final UUID USER_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");

    @Test
    void mapsOnlyThePrincipalAnchorOptionsAndPreferenceToTheServiceCommand() {
        UserProductVariantSelectionService service = mock(UserProductVariantSelectionService.class);
        when(service.select(any(), any())).thenReturn(
                new UserProductVariantSelectionResult(details(), null, false));
        UserProductVariantSelectionController controller = new UserProductVariantSelectionController(service);
        SelectUserProductVariantRequest request = new SelectUserProductVariantRequest(
                "offer-v2_anchor",
                List.of(
                        new SelectUserProductVariantRequest.SelectedOption("Color", "Blue"),
                        new SelectUserProductVariantRequest.SelectedOption("Size", "L")
                ),
                "Size"
        );

        var response = controller.select(jwt(), request);

        assertThat(response.details().productId()).isEqualTo("product-1");
        assertThat(response.details().options()).singleElement().satisfies(option -> {
            assertThat(option.name()).isEqualTo("Size");
            assertThat(option.valueDetails()).singleElement().satisfies(value -> {
                assertThat(value.value()).isEqualTo("L");
                assertThat(value.available()).isTrue();
                assertThat(value.exists()).isTrue();
            });
        });
        assertThat(response.selectedOfferKey()).isNull();
        assertThat(response.selectedOffer()).isNull();
        assertThat(response.cartable()).isFalse();

        ArgumentCaptor<EnsureUserProfileCommand> profile = ArgumentCaptor.forClass(EnsureUserProfileCommand.class);
        ArgumentCaptor<SelectUserProductVariantCommand> command =
                ArgumentCaptor.forClass(SelectUserProductVariantCommand.class);
        verify(service).select(profile.capture(), command.capture());
        assertThat(profile.getValue().id()).isEqualTo(USER_ID);
        assertThat(command.getValue().userId()).isEqualTo(USER_ID);
        assertThat(command.getValue().anchorOfferKey()).isEqualTo("offer-v2_anchor");
        assertThat(command.getValue().preferredOptionName()).isEqualTo("Size");
        assertThat(command.getValue().selectedOptions())
                .extracting(SelectUserProductVariantCommand.SelectedOption::name)
                .containsExactly("Color", "Size");
    }

    @Test
    void requestAllowsEmptyOptionsButValidatesTheBoundaryShape() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();

            assertThat(validator.validate(new SelectUserProductVariantRequest(
                    "offer-v2_anchor", List.of(), null))).isEmpty();
            assertThat(validator.validate(new SelectUserProductVariantRequest(
                    "offer-v2_anchor", null, null)))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("selectedOptions");
            assertThat(validator.validate(new SelectUserProductVariantRequest(
                    "offer-v2_anchor",
                    List.of(new SelectUserProductVariantRequest.SelectedOption(" ", "L")),
                    null)))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("selectedOptions[0].name");
        }
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .claim("email", "shopper@example.com")
                .claim("user_metadata", Map.of("full_name", "Ada Shopper"))
                .issuedAt(Instant.parse("2026-07-14T10:00:00Z"))
                .expiresAt(Instant.parse("2026-07-14T11:00:00Z"))
                .build();
    }

    private RehydratedProductDetails details() {
        RehydratedProductDetails.Variant selected = new RehydratedProductDetails.Variant(
                "variant-l",
                "large",
                "Large",
                null,
                null,
                "25.00",
                "USD",
                null,
                null,
                "SKU-L",
                null,
                null,
                List.of(),
                true,
                List.of(new RehydratedProductDetails.SelectedOption("Size", "L")),
                List.of(),
                List.of(),
                List.of()
        );
        return new RehydratedProductDetails(
                "product-1",
                "product",
                "Product",
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new RehydratedProductDetails.Option(
                        "Size",
                        List.of("L"),
                        List.of(new RehydratedProductDetails.OptionValue("L", true, true))
                )),
                List.of(new RehydratedProductDetails.SelectedOption("Size", "L")),
                List.of(selected),
                1,
                null,
                null,
                false,
                selected,
                List.of("SKU-L"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                "Merchant"
        );
    }
}
