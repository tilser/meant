package com.meant.api.module.user.controller.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.service.query.RehydrateUserCanonicalProductsQuery;
import jakarta.validation.Validation;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UserCanonicalProductRehydrationRequestTest {

    private static final UUID USER_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");

    @Test
    void acceptsOneToTwentyOneBoundedCanonicalKeysAtBothBoundaries() {
        List<String> keys = IntStream.rangeClosed(1, 21)
                .mapToObj(index -> "grouped-product-v3_" + index)
                .toList();

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(
                    new UserCanonicalProductRehydrationRequest(keys))).isEmpty();
            assertThat(factory.getValidator().validate(
                    new RehydrateUserCanonicalProductsQuery(USER_ID, keys))).isEmpty();
        }
    }

    @Test
    void rejectsEmptyOversizedBlankAndOverlongCanonicalKeys() {
        List<String> tooMany = IntStream.rangeClosed(1, 22)
                .mapToObj(index -> "grouped-product-v3_" + index)
                .toList();

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(
                    new UserCanonicalProductRehydrationRequest(List.of()))).isNotEmpty();
            assertThat(validator.validate(
                    new UserCanonicalProductRehydrationRequest(tooMany))).isNotEmpty();
            assertThat(validator.validate(
                    new UserCanonicalProductRehydrationRequest(List.of("   ")))).isNotEmpty();
            assertThat(validator.validate(
                    new UserCanonicalProductRehydrationRequest(List.of("x".repeat(201))))).isNotEmpty();
            assertThat(validator.validate(
                    new RehydrateUserCanonicalProductsQuery(USER_ID, tooMany))).isNotEmpty();
        }
    }
}
