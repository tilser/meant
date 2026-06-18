package com.meant.api.module.cart.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateCartArguments(
        @JsonProperty("cart_id")
        String cartId,
        @JsonProperty("add_items")
        List<CartAddItem> addItems,
        @JsonProperty("update_items")
        List<CartUpdateItem> updateItems,
        @JsonProperty("remove_line_ids")
        List<String> removeLineIds,
        @JsonProperty("buyer_identity")
        Map<String, Object> buyerIdentity,
        @JsonProperty("delivery_addresses_to_add")
        List<Map<String, Object>> deliveryAddressesToAdd,
        @JsonProperty("delivery_addresses_to_replace")
        List<Map<String, Object>> deliveryAddressesToReplace,
        @JsonProperty("selected_delivery_options")
        List<Map<String, Object>> selectedDeliveryOptions,
        @JsonProperty("discount_codes")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<String> discountCodes,
        @JsonProperty("gift_card_codes")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<String> giftCardCodes,
        String note
) {
}
