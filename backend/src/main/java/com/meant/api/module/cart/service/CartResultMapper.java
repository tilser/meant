package com.meant.api.module.cart.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.service.dto.CartDeliveryGroupResult;
import com.meant.api.module.cart.service.dto.CartMessageResult;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
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
        UcpCartResponse response = storedResponse(cart);
        return CartResult.from(cart, deliveryGroups(response), messages(response));
    }

    private UcpCartResponse storedResponse(Cart cart) {
        String rawCartResponse = cart.getRawCartResponse();
        if (rawCartResponse == null || rawCartResponse.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawCartResponse, UcpCartResponse.class);
        } catch (JacksonException exception) {
            log.warn("Could not parse stored cart response for cart {}: {}", cart.getId(), exception.getMessage());
            return null;
        }
    }

    private List<CartDeliveryGroupResult> deliveryGroups(UcpCartResponse response) {
        if (response == null || response.cart() == null) {
            return List.of();
        }
        return safeNonNullList(response.cart().deliveryGroups()).stream()
                .map(CartDeliveryGroupResult::from)
                .filter(group -> group != null)
                .toList();
    }

    private List<CartMessageResult> messages(UcpCartResponse response) {
        if (response == null) {
            return List.of();
        }
        return java.util.stream.Stream.concat(
                        safeNonNullList(response.messages()).stream(),
                        response.cart() == null
                                ? java.util.stream.Stream.empty()
                                : safeNonNullList(response.cart().messages()).stream()
                )
                .map(CartMessageResult::from)
                .filter(message -> message != null)
                .toList();
    }
}
