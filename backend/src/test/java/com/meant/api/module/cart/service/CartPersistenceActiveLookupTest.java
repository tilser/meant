package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.repository.CartRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;

class CartPersistenceActiveLookupTest {

    @Test
    void exactRoutingScopeLookupRequestsOnlyTheNewestActiveUnexpiredCandidate() {
        UUID userId = UUID.randomUUID();
        String routingScopeKey = "SHOPIFY:integration:verified-merchant";
        Cart expected = Cart.builder().userId(userId).routingScopeKey(routingScopeKey).build();
        CartRepository repository = mock(CartRepository.class);
        when(repository.findActiveForRoutingScope(
                eq(userId), eq(routingScopeKey), any(), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(expected));
        CartPersistenceService service = new CartPersistenceService(repository, new ObjectMapper());

        assertThat(service.findActiveCartByRoutingScope(userId, routingScopeKey))
                .containsSame(expected);
        verify(repository).findActiveForRoutingScope(
                eq(userId), eq(routingScopeKey), any(), eq(PageRequest.of(0, 1)));
    }
}
