package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddress;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddressSelection;
import com.meant.api.plugin.cart.common.dto.CartDeliveryOptionSelection;
import com.meant.api.plugin.cart.common.dto.CartToolArguments;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CartFulfillmentReplacementServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final CartFulfillmentReplacementService service = new CartFulfillmentReplacementService();

    @Test
    void updatingOneOfTwoMethodsPreservesTheOtherMethodAndUnrelatedState() {
        CartToolArguments.Fulfillment current = fulfillment(methodA(), methodB());

        CartToolArguments.Fulfillment merged = service.merge(
                current, null, null, List.of(option(null, "group-a", "standard")));

        assertThat(merged.methods()).hasSize(2);
        assertThat(merged.methods().getFirst().groups().getFirst().selectedOptionId()).isEqualTo("standard");
        assertThat(merged.methods().getFirst().groups().getFirst().extensions())
                .containsKey("merchant_extension");
        assertThat(merged.methods().getFirst().destinations()).isEqualTo(current.methods().getFirst().destinations());
        assertThat(merged.methods().get(1)).isEqualTo(current.methods().get(1));
    }

    @Test
    void requestPermutationProducesTheSameDeterministicMethodState() {
        CartToolArguments.Fulfillment current = fulfillment(methodA(), methodB());
        CartDeliveryOptionSelection updateA = option(null, "group-a", "standard");
        CartDeliveryOptionSelection updateB = option(null, "group-b", "pickup-later");

        CartToolArguments.Fulfillment first = service.merge(current, null, null, List.of(updateB, updateA));
        CartToolArguments.Fulfillment second = service.merge(current, null, null, List.of(updateA, updateB));

        assertThat(first).isEqualTo(second);
        assertThat(first.methods()).extracting(CartToolArguments.FulfillmentMethod::id)
                .containsExactly("method-a", "method-b");
    }

    @Test
    void ambiguousMultiMethodRequestFailsClosedBeforeReplacement() {
        CartToolArguments.Fulfillment current = fulfillment(methodA(), methodB());

        assertThatThrownBy(() -> service.merge(
                current, List.of(address(null, "new-destination", "10002")), null, null))
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
                current, null, List.of(new CartDeliveryAddressSelection("method-a", null, null)), null);

        assertThat(merged.methods().getFirst().destinations()).isEmpty();
        assertThat(merged.methods().getFirst().selectedDestinationId()).isNull();
        assertThat(merged.methods().getFirst().groups()).isEqualTo(current.methods().getFirst().groups());
        assertThat(merged.methods().get(1)).isEqualTo(current.methods().get(1));
    }

    @Test
    void singleMethodPartialUpdateRetainsKnownAndExtensionState() {
        CartToolArguments.Fulfillment current = fulfillment(methodA());

        CartToolArguments.Fulfillment merged = service.merge(
                current,
                null,
                List.of(address(null, "home-a", "10003")),
                List.of(option(null, "group-a", "standard")));

        CartToolArguments.FulfillmentMethod method = merged.methods().getFirst();
        assertThat(method.id()).isEqualTo("method-a");
        assertThat(method.type()).isEqualTo("shipping");
        assertThat(method.extensions()).containsKey("merchant_extension");
        assertThat(method.destinations()).singleElement().satisfies(destination -> {
            assertThat(destination.id()).isEqualTo("home-a");
            assertThat(destination.postalCode()).isEqualTo("10003");
            assertThat(destination.extensions()).containsKey("destination_extension");
        });
        assertThat(method.groups().getFirst().selectedOptionId()).isEqualTo("standard");
        assertThat(method.groups().getFirst().extensions()).containsKey("merchant_extension");
    }

    @Test
    void replacingDestinationsCannotRetainASelectionThatNoLongerExists() {
        CartToolArguments.Fulfillment merged = service.merge(
                fulfillment(methodA()), null, List.of(address(null, "new-home", "10004")), null);

        assertThat(merged.methods().getFirst().destinations())
                .extracting(CartDeliveryAddress::id)
                .containsExactly("new-home");
        assertThat(merged.methods().getFirst().selectedDestinationId()).isNull();
    }

    private CartToolArguments.Fulfillment fulfillment(CartToolArguments.FulfillmentMethod... methods) {
        return new CartToolArguments.Fulfillment(List.of(methods));
    }

    private CartToolArguments.FulfillmentMethod methodA() {
        return method("method-a", "shipping", "home-a", "10001", "group-a", "express", "keep-a");
    }

    private CartToolArguments.FulfillmentMethod methodB() {
        return method("method-b", "pickup", "store-b", "20001", "group-b", "pickup-now", "keep-b");
    }

    private CartToolArguments.FulfillmentMethod method(
            String id, String type, String destinationId, String postalCode,
            String groupId, String selectedOptionId, String groupExtension
    ) {
        CartDeliveryAddress destination = new CartDeliveryAddress(
                destinationId, null, null, null, null, null, null, null, postalCode, null,
                Map.of("destination_extension", JSON.valueToTree("keep-destination")));
        CartToolArguments.FulfillmentGroup group = new CartToolArguments.FulfillmentGroup(
                groupId, List.of(), List.of(), selectedOptionId,
                Map.of("merchant_extension", JSON.valueToTree(groupExtension)));
        return new CartToolArguments.FulfillmentMethod(
                id, type, List.of(), List.of(destination), destinationId, List.of(group),
                Map.of("merchant_extension", JSON.valueToTree(id + "-extension")));
    }

    private CartDeliveryAddressSelection address(String methodId, String id, String postalCode) {
        return new CartDeliveryAddressSelection(methodId, null,
                new CartDeliveryAddress(id, null, null, null, null, null, null, null, postalCode, null));
    }

    private CartDeliveryOptionSelection option(String methodId, String groupId, String selectedOptionId) {
        return new CartDeliveryOptionSelection(methodId, groupId, selectedOptionId);
    }
}
