package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.user.exception.PermanentAccountRequiredException;
import com.meant.api.module.user.service.dto.AuthenticatedUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermanentAccountPolicyTest {

    @Test
    void rejectsAnonymousIdentityWithStableProblemCode() {
        AuthenticatedUser anonymous = new AuthenticatedUser(UUID.randomUUID(), null, null, null, true);

        assertThatThrownBy(() -> PermanentAccountPolicy.requirePermanentAccount(anonymous))
                .isInstanceOf(PermanentAccountRequiredException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                                ((PermanentAccountRequiredException) error).getErrorCode().getValue())
                        .isEqualTo("PERMANENT_ACCOUNT_REQUIRED"));
    }

    @Test
    void allowsPermanentIdentity() {
        AuthenticatedUser permanent = new AuthenticatedUser(
                UUID.randomUUID(), "shopper@example.com", null, null, false);

        assertThatCode(() -> PermanentAccountPolicy.requirePermanentAccount(permanent))
                .doesNotThrowAnyException();
    }
}
