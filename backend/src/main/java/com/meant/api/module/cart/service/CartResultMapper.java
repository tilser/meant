package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.service.dto.CartDeliveryGroupResult;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.cart.service.dto.CartToolResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartResultMapper {

    private final ObjectMapper objectMapper;

    public CartResult from(Cart cart) {
        return CartResult.from(cart, deliveryGroups(cart));
    }

    private List<CartDeliveryGroupResult> deliveryGroups(Cart cart) {
        String rawCartResponse = cart.getRawCartResponse();
        if (rawCartResponse == null || rawCartResponse.isBlank()) {
            return List.of();
        }
        try {
            CartToolResponse response = objectMapper.readValue(rawCartResponse, CartToolResponse.class);
            if (response == null || response.cart() == null) {
                return List.of();
            }
            return safeNonNullList(response.cart().deliveryGroups()).stream()
                    .map(CartDeliveryGroupResult::from)
                    .toList();
        } catch (JacksonException exception) {
            log.warn("Could not parse stored cart delivery groups for cart {}: {}", cart.getId(), exception.getMessage());
            return List.of();
        }
    }
}
