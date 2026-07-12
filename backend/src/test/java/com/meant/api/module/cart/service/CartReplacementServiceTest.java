package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.command.UpdateCartCommand;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartToolArguments;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.update.dto.CartReplacementState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CartReplacementServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CartReplacementService service;
    private Cart cart;
    private CartLine first;
    private CartLine second;

    @BeforeEach
    void setUp() throws Exception {
        service = new CartReplacementService(
                new CartLineOfferIdentityMapper(objectMapper), new CartFulfillmentReplacementService());
        first = line("remote-a", "product-a", "variant-a", "Black", 1);
        second = line("remote-b", "product-b", "variant-b", "Large", 2);
        cart = Cart.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).routingScopeKey("SHOPIFY:integration:one")
                .remoteCartId("cart-1").endpoint("https://shop.test/api/ucp/mcp")
                .remoteCartIdHash("hash").rawCartResponse("{}").totalQuantity(3).active(true)
                .createdAt(Instant.now()).updatedAt(Instant.now()).refreshedAt(Instant.now())
                .lines(new java.util.ArrayList<>(List.of(first, second))).build();
    }

    @Test
    void quantityMutationPreservesEveryTransientRemoteFieldAndExactIdentity() {
        UpdateCartCommand command = command(
                List.of(new UpdateCartCommand.UpdateItem(first.getId(), "remote-a", 4)),
                null, null, null, null, null, null);

        var request = service.build(cart, command, List.of(), Map.of("postal_code", "10001"), remoteCart());
        CartReplacementState state = request.replacementState();

        assertThat(state.lineItems()).extracting(CartAddItem::quantity).containsExactly(4, 2);
        assertThat(state.lineItems().getFirst().productId()).isEqualTo("product-a");
        assertThat(state.lineItems().getFirst().selectedOptions())
                .containsExactly(new CartAddItem.SelectedOption("variant", "Color", "Black"));
        assertThat(state.buyer()).containsEntry("email", "buyer@example.test");
        assertThat(state.context()).containsEntry("address_country", "US").containsEntry("postal_code", "10001");
        assertThat(state.signals()).containsEntry("com.meant.observed", "trusted");
        assertThat(state.discounts().codes()).containsExactly("SAVE10");
        assertThat(state.giftCardCodes()).containsExactly("GIFT1");
        assertThat(state.fulfillment().methods().getFirst().get("groups")).isEqualTo(
                List.of(Map.of("id", "delivery-a", "selected_option_id", "express")));
        assertThat(state.note()).isEqualTo("leave at reception");
    }

    @Test
    void selectedDeliveryMutationPreservesRemoteDestinationAndOtherState() {
        UpdateCartCommand command = command(List.of(), null, null, null,
                List.of(Map.of("group_id", "delivery-a", "selected_option_id", "standard")), null, null);

        CartReplacementState state = service.build(cart, command, List.of(), Map.of(), remoteCart())
                .replacementState();

        assertThat(state.fulfillment().methods().getFirst().get("destinations"))
                .isEqualTo(List.of(Map.of("id", "home", "postal_code", "10001")));
        assertThat(state.buyer()).containsEntry("email", "buyer@example.test");
        assertThat(state.discounts().codes()).containsExactly("SAVE10");
        assertThat(state.note()).isEqualTo("leave at reception");
    }

    @Test
    void exactReconciliationIsOrderIndependent() {
        CartReplacementState intended = state(item("product-a", "variant-a", "Black", 1),
                item("product-b", "variant-b", "Large", 2));
        UcpCartResponse response = response(List.of(
                remoteLine("other-b", "product-b", "variant-b", "Large", 2),
                remoteLine("other-a", "product-a", "variant-a", "Black", 1)));

        assertThat(service.proves(response, intended)).isTrue();
    }

    @Test
    void exactReconciliationRejectsDifferentOrOmittedConfigurationAndLineSetChanges() {
        CartAddItem configured = new CartAddItem(
                "product-a", "variant-a",
                List.of(new CartAddItem.SelectedOption("variant", "Color", "Black")),
                List.of(new CartAddItem.Component("component-p", "component-v", 1,
                        List.of(new CartAddItem.SelectedOption(null, "Size", "M")))),
                new CartAddItem.SellingPlan("group-1", "plan-1", List.of(new CartAddItem.Option("Every", "Month"))),
                1);
        CartReplacementState intended = state(configured);

        assertThat(service.proves(response(List.of(new UcpCartResponse.Line(
                "line", 1, null, new UcpCartResponse.Merchandise(
                "variant-a", null, new UcpCartResponse.Product("product-a", null),
                "product-a", List.of(), List.of(), null)))), intended)).isFalse();
        assertThat(service.proves(response(List.of(remoteLine(
                "line", "product-a", "variant-a", "White", 1))), intended)).isFalse();
        assertThat(service.proves(response(List.of(new UcpCartResponse.Line(
                "line", 1, null, configuredMerchandise(
                List.of(new CartAddItem.Component("component-p", "other-component", 1, List.of())),
                configured.sellingPlan())))), intended)).isFalse();
        assertThat(service.proves(response(List.of(new UcpCartResponse.Line(
                "line", 1, null, configuredMerchandise(configured.components(),
                new CartAddItem.SellingPlan("group-1", "other-plan", List.of()))))), intended)).isFalse();
        assertThat(service.proves(response(List.of()), intended)).isFalse();
        assertThat(service.proves(response(List.of(
                remoteLine("line", "product-a", "variant-a", "Black", 1),
                remoteLine("extra", "product-b", "variant-b", "Large", 1))), intended)).isFalse();
    }

    @Test
    void conflictingLocalAndRemoteIdentifiersAreRejectedBeforeAssembly() {
        UpdateCartCommand mismatch = command(
                List.of(new UpdateCartCommand.UpdateItem(first.getId(), second.getRemoteCartLineId(), 3)),
                null, null, null, null, null, null);
        UpdateCartCommand exact = command(
                List.of(new UpdateCartCommand.UpdateItem(first.getId(), first.getRemoteCartLineId(), 3)),
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.validateIdentifiers(cart, mismatch))
                .isInstanceOf(CartException.class)
                .hasMessageContaining("different lines");
        service.validateIdentifiers(cart, exact);
    }

    @Test
    void removalUsesUniqueImmutableIdentityWhenMerchantRotatesRemoteLineIds() {
        UpdateCartCommand command = new UpdateCartCommand(
                cart.getId(), cart.getUserId(), List.of(), List.of(), List.of(first.getId()), List.of(),
                null, null, null, null, null, null, null);
        UcpCartResponse rotated = response(List.of(
                remoteLine("rotated-b", "product-b", "variant-b", "Large", 2),
                remoteLine("rotated-a", "product-a", "variant-a", "Black", 1)));

        CartReplacementState state = service.build(cart, command, List.of(), Map.of(), rotated)
                .replacementState();

        assertThat(state.lineItems()).extracting(CartAddItem::productVariantId)
                .containsExactly("variant-b");
        assertThat(state.lineItems()).extracting(CartAddItem::quantity).containsExactly(2);
    }

    @Test
    void removalAcceptsOmittedRemoteOptionsOnlyWhenVariantMatchIsUnique() {
        UpdateCartCommand command = new UpdateCartCommand(
                cart.getId(), cart.getUserId(), List.of(), List.of(), List.of(first.getId()), List.of(),
                null, null, null, null, null, null, null);
        UcpCartResponse rotated = response(List.of(
                remoteLineWithoutOptions("rotated-a", "product-a", "variant-a", 1),
                remoteLineWithoutOptions("rotated-b", "product-b", "variant-b", 2)));

        CartReplacementState state = service.build(cart, command, List.of(), Map.of(), rotated)
                .replacementState();

        assertThat(state.lineItems()).extracting(CartAddItem::productVariantId)
                .containsExactly("variant-b");
    }

    private UpdateCartCommand command(
            List<UpdateCartCommand.UpdateItem> updates,
            Map<String, Object> buyer,
            List<Map<String, Object>> addAddresses,
            List<Map<String, Object>> replaceAddresses,
            List<Map<String, Object>> delivery,
            List<String> discounts,
            List<String> gifts
    ) {
        return new UpdateCartCommand(cart.getId(), cart.getUserId(), List.of(), updates, List.of(), List.of(),
                buyer, addAddresses, replaceAddresses, delivery, discounts, gifts, null);
    }

    private CartReplacementState state(CartAddItem... items) {
        return new CartReplacementState(List.of(items), Map.of(), Map.of(), Map.of(), null, null, List.of(), null);
    }

    private UcpCartResponse remoteCart() {
        CartToolArguments.Fulfillment fulfillment = new CartToolArguments.Fulfillment(List.of(Map.of(
                "type", "shipping",
                "destinations", List.of(Map.of("id", "home", "postal_code", "10001")),
                "groups", List.of(Map.of("id", "delivery-a", "selected_option_id", "express")))));
        UcpCartResponse.Cart remote = new UcpCartResponse.Cart(
                "cart-1", null, null, null,
                List.of(remoteLine("remote-a", "product-a", "variant-a", "Black", 1),
                        remoteLine("remote-b", "product-b", "variant-b", "Large", 2)),
                null, 3, null, null,
                List.of(new UcpCartResponse.AppliedCode("SAVE10", null, true, null)), List.of(), List.of(),
                List.of(new UcpCartResponse.AppliedCode("GIFT1", null, true, null)), List.of(), List.of(), List.of(),
                Map.of("email", "buyer@example.test"), Map.of("address_country", "US"),
                Map.of("com.meant.observed", "trusted"), fulfillment,
                new CartToolArguments.Discounts(List.of("SAVE10")), "leave at reception");
        return new UcpCartResponse(null, remote, List.of(), List.of());
    }

    private UcpCartResponse response(List<UcpCartResponse.Line> lines) {
        UcpCartResponse.Cart remote = new UcpCartResponse.Cart(
                "cart-1", null, null, null, lines, null,
                lines.stream().map(UcpCartResponse.Line::quantity).reduce(0, Integer::sum),
                null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        return new UcpCartResponse(null, remote, List.of(), List.of());
    }

    private UcpCartResponse.Line remoteLine(
            String id, String product, String variant, String color, int quantity) {
        return new UcpCartResponse.Line(id, quantity, null, new UcpCartResponse.Merchandise(
                variant, null, new UcpCartResponse.Product(product, null), product,
                List.of(new CartAddItem.SelectedOption("variant", "Color", color)), List.of(), null));
    }

    private UcpCartResponse.Line remoteLineWithoutOptions(
            String id, String product, String variant, int quantity) {
        return new UcpCartResponse.Line(id, quantity, null, new UcpCartResponse.Merchandise(
                variant, null, new UcpCartResponse.Product(product, null), product,
                List.of(), List.of(), null));
    }

    private CartAddItem item(String product, String variant, String color, int quantity) {
        return new CartAddItem(product, variant,
                List.of(new CartAddItem.SelectedOption("variant", "Color", color)), List.of(), null, quantity);
    }

    private UcpCartResponse.Merchandise configuredMerchandise(
            List<CartAddItem.Component> components, CartAddItem.SellingPlan sellingPlan) {
        return new UcpCartResponse.Merchandise(
                "variant-a", null, new UcpCartResponse.Product("product-a", null), "product-a",
                List.of(new CartAddItem.SelectedOption("variant", "Color", "Black")), components, sellingPlan);
    }

    private CartLine line(String remoteId, String product, String variant, String color, int quantity) throws Exception {
        return CartLine.builder()
                .id(UUID.randomUUID()).remoteCartLineId(remoteId).productId(product).productVariantId(variant)
                .externalProductId(product).externalVariantId(variant).offerKey("offer-" + remoteId)
                .selectedOptionsJson(objectMapper.writeValueAsString(
                        List.of(new ProductAttribute("variant", "Color", color))))
                .componentsJson("[]").quantity(quantity).rawLineResponse("{}")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }
}
