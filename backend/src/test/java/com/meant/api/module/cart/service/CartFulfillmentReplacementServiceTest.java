package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.plugin.cart.common.dto.CartToolArguments;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CartFulfillmentReplacementServiceTest {
    private final CartFulfillmentReplacementService service = new CartFulfillmentReplacementService();

    @Test
    void updatingOneOfTwoMethodsPreservesTheOtherMethodAndUnrelatedState() {
        CartToolArguments.Fulfillment current = fulfillment(methodA(), methodB());

        CartToolArguments.Fulfillment merged = service.merge(
                current, null, null,
                List.of(Map.of("group_id", "group-a", "selected_option_id", "standard")));

        assertThat(merged.methods()).hasSize(2);
        assertThat(groups(merged, 0)).containsExactly(
                Map.of("id", "group-a", "selected_option_id", "standard", "merchant_extension", "keep-a"));
        assertThat(destinations(merged, 0)).isEqualTo(destinations(current, 0));
        assertThat(merged.methods().get(1)).isEqualTo(current.methods().get(1));
    }

    @Test
    void requestPermutationProducesTheSameDeterministicMethodState() {
        CartToolArguments.Fulfillment current = fulfillment(methodA(), methodB());
        Map<String, Object> updateA = Map.of("group_id", "group-a", "selected_option_id", "standard");
        Map<String, Object> updateB = Map.of("group_id", "group-b", "selected_option_id", "pickup-later");

        CartToolArguments.Fulfillment first = service.merge(current, null, null, List.of(updateB, updateA));
        CartToolArguments.Fulfillment second = service.merge(current, null, null, List.of(updateA, updateB));

        assertThat(first).isEqualTo(second);
        assertThat(first.methods()).extracting(method -> method.get("id"))
                .containsExactly("method-a", "method-b");
    }

    @Test
    void ambiguousMultiMethodRequestFailsClosedBeforeReplacement() {
        CartToolArguments.Fulfillment current = fulfillment(methodA(), methodB());

        assertThatThrownBy(() -> service.merge(
                current, List.of(Map.of("id", "new-destination", "postal_code", "10002")), null, null))
                .isInstanceOf(CartException.class)
                .hasMessageContaining("exactly one existing method");
        assertThatThrownBy(() -> service.merge(current, null, List.of(), null))
                .isInstanceOf(CartException.class)
                .hasMessageContaining("exactly one existing method");
    }

    @Test
    void explicitClearOnlyClearsTheTargetedMethodState() {
        CartToolArguments.Fulfillment current = fulfillment(methodA(), methodB());

        CartToolArguments.Fulfillment merged = service.merge(
                current, null, List.of(Map.of("method_id", "method-a")), null);

        assertThat(destinations(merged, 0)).isEmpty();
        assertThat(groups(merged, 0)).isEqualTo(groups(current, 0));
        assertThat(merged.methods().get(1)).isEqualTo(current.methods().get(1));
    }

    @Test
    void singleMethodPartialUpdateRetainsExistingBehaviorAndCompleteState() {
        CartToolArguments.Fulfillment current = fulfillment(methodA());

        CartToolArguments.Fulfillment merged = service.merge(
                current,
                null,
                List.of(Map.of("id", "home-a", "postal_code", "10003")),
                List.of(Map.of("group_id", "group-a", "selected_option_id", "standard")));

        assertThat(merged.methods()).singleElement().satisfies(method -> {
            assertThat(method).containsEntry("id", "method-a").containsEntry("type", "shipping");
            assertThat(method).containsEntry("merchant_extension", "method-a-extension");
        });
        assertThat(destinations(merged, 0)).containsExactly(Map.of("id", "home-a", "postal_code", "10003"));
        assertThat(groups(merged, 0)).containsExactly(
                Map.of("id", "group-a", "selected_option_id", "standard", "merchant_extension", "keep-a"));
    }

    private CartToolArguments.Fulfillment fulfillment(Map<String, Object>... methods) {
        return new CartToolArguments.Fulfillment(List.of(methods));
    }

    private Map<String, Object> methodA() {
        return Map.of(
                "id", "method-a",
                "type", "shipping",
                "merchant_extension", "method-a-extension",
                "destinations", List.of(Map.of("id", "home-a", "postal_code", "10001")),
                "groups", List.of(Map.of(
                        "id", "group-a", "selected_option_id", "express", "merchant_extension", "keep-a")));
    }

    private Map<String, Object> methodB() {
        return Map.of(
                "id", "method-b",
                "type", "pickup",
                "merchant_extension", "method-b-extension",
                "destinations", List.of(Map.of("id", "store-b", "postal_code", "20001")),
                "groups", List.of(Map.of(
                        "id", "group-b", "selected_option_id", "pickup-now", "merchant_extension", "keep-b")));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> destinations(CartToolArguments.Fulfillment fulfillment, int method) {
        return (List<Map<String, Object>>) fulfillment.methods().get(method).get("destinations");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> groups(CartToolArguments.Fulfillment fulfillment, int method) {
        return (List<Map<String, Object>>) fulfillment.methods().get(method).get("groups");
    }
}
