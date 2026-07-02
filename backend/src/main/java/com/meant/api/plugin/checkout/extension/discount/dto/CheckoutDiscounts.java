package com.meant.api.plugin.checkout.extension.discount.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CheckoutDiscounts(
        List<String> codes,
        List<AppliedDiscount> applied
) {

    public CheckoutDiscounts {
        codes = codes == null ? List.of() : List.copyOf(codes);
        applied = applied == null ? List.of() : List.copyOf(applied);
    }

    public boolean empty() {
        return codes.isEmpty() && applied.isEmpty();
    }

    public static CheckoutDiscounts codes(List<String> codes) {
        return new CheckoutDiscounts(codes, List.of());
    }
}
