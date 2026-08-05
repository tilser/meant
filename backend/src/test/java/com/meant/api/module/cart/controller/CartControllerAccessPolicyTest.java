package com.meant.api.module.cart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.common.security.PermanentAccountRequired;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CartControllerAccessPolicyTest {

    private static final Set<String> CHECKOUT_METHODS = Set.of(
            "checkout",
            "bootstrapEmbeddedCheckout",
            "acknowledgeEmbeddedCheckoutOpened",
            "completeEmbeddedCheckout",
            "cancelEmbeddedCheckout",
            "updateCheckout",
            "assistCheckout",
            "recordCheckoutConsent",
            "completeCheckout",
            "cancelCheckout"
    );

    @Test
    void guestsCanManageCartsButEveryCheckoutOperationRequiresAPermanentAccount() {
        assertThat(CartController.class.isAnnotationPresent(PermanentAccountRequired.class)).isFalse();

        assertThat(Set.of(CartController.class.getDeclaredMethods()).stream()
                .filter(method -> method.isAnnotationPresent(PermanentAccountRequired.class))
                .map(Method::getName))
                .containsExactlyInAnyOrderElementsOf(CHECKOUT_METHODS);

        assertThat(Set.of("create", "get", "update", "cancel"))
                .allSatisfy(methodName -> assertThat(findMethod(methodName)
                        .isAnnotationPresent(PermanentAccountRequired.class)).isFalse());
    }

    private Method findMethod(String name) {
        return Set.of(CartController.class.getDeclaredMethods()).stream()
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
