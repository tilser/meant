package com.meant.api.module.cart.service;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.repository.CartRepository;
import com.meant.api.module.cart.service.command.TransferCartOwnershipCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Moves live remote-cart ownership when a guest identity is claimed by a permanent account. */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class CartOwnershipTransferService {
    private final CartRepository cartRepository;
    private final Clock clock;

    @Transactional
    public List<UUID> transfer(@NotNull @Valid TransferCartOwnershipCommand command) {
        if (command.sourceUserId().equals(command.targetUserId())) {
            return List.of();
        }

        Instant transferredAt = clock.instant();
        List<Cart> carts = cartRepository.findActiveForOwnershipTransfer(
                command.sourceUserId(), transferredAt);
        carts.forEach(cart -> cart.transferOwnership(command.targetUserId(), transferredAt));
        log.info(
                "Guest carts transferred. sourceUserId={}, targetUserId={}, cartCount={}",
                command.sourceUserId(), command.targetUserId(), carts.size()
        );
        return carts.stream().map(Cart::getId).toList();
    }
}
