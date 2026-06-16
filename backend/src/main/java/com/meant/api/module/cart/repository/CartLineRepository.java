package com.meant.api.module.cart.repository;

import com.meant.api.module.cart.entity.CartLine;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartLineRepository extends JpaRepository<CartLine, UUID> {
}
