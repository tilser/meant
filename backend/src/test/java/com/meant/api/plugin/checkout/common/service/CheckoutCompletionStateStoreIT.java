package com.meant.api.plugin.checkout.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.meant.api.PostgresIntegrationTest;
import com.meant.api.plugin.checkout.common.entity.CheckoutCompletionStatus;
import com.meant.api.plugin.checkout.common.service.command.AuthorizeCheckoutCompletionCommand;
import com.meant.api.plugin.checkout.common.service.command.StartCheckoutCompletionCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CheckoutCompletionStateStoreIT extends PostgresIntegrationTest {

    @Autowired
    private CheckoutCompletionStateStore store;

    @Test
    void readOnlyFindUsesNonLockingQueryOnPostgres() {
        String checkoutId = "co_" + UUID.randomUUID();
        store.authorize(new AuthorizeCheckoutCompletionCommand(checkoutId, null));

        assertThatCode(() -> store.find(new StartCheckoutCompletionCommand(checkoutId)))
                .doesNotThrowAnyException();
        assertThat(store.find(new StartCheckoutCompletionCommand(checkoutId)).getStatus())
                .isEqualTo(CheckoutCompletionStatus.AUTHORIZED_TO_COMPLETE);
    }
}
