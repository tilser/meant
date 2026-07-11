package com.meant.api.module.cart.service;

import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.catalog.service.dto.OfferComponentIdentity;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.SellingPlanIdentity;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Restores the immutable selected-offer identity needed for PUT-style cart replacement. */
@Component
@RequiredArgsConstructor
public class CartLineOfferIdentityMapper {
    private final ObjectMapper objectMapper;

    public CartAddItem toItem(CartLine line, int quantity) {
        if (line.getOfferKey() == null) {
            return new CartAddItem(line.getProductId(), line.getProductVariantId(),
                    List.of(), List.of(), null, quantity);
        }
        try {
            List<ProductAttribute> options = readList(line.getSelectedOptionsJson(), new TypeReference<>() {});
            List<OfferComponentIdentity> components = readList(line.getComponentsJson(), new TypeReference<>() {});
            SellingPlanIdentity sellingPlan = line.getSellingPlanJson() == null ? null
                    : objectMapper.readValue(line.getSellingPlanJson(), SellingPlanIdentity.class);
            return new CartAddItem(
                    first(line.getExternalProductId(), line.getProductId()),
                    first(line.getExternalVariantId(), line.getProductVariantId()),
                    options.stream().map(option -> new CartAddItem.SelectedOption(
                            option.group(), option.name(), option.value())).toList(),
                    components.stream().map(component -> new CartAddItem.Component(
                            component.externalProductIdentity().value(),
                            component.externalVariantIdentity() == null
                                    ? null : component.externalVariantIdentity().value(),
                            component.quantity(),
                            component.selectedOptions().stream().map(option -> new CartAddItem.SelectedOption(
                                    option.group(), option.name(), option.value())).toList())).toList(),
                    sellingPlan == null ? null : new CartAddItem.SellingPlan(
                            sellingPlan.groupReference() == null ? null : sellingPlan.groupReference().value(),
                            sellingPlan.planReference() == null ? null : sellingPlan.planReference().value(),
                            sellingPlan.options().stream().map(option ->
                                    new CartAddItem.Option(option.name(), option.value())).toList()),
                    quantity
            );
        } catch (JacksonException exception) {
            throw CartException.binding(CartException.BindingFailure.IDENTITY_MISMATCH,
                    "Stored cart line identity is malformed");
        }
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type) throws JacksonException {
        return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, type);
    }

    private String first(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
