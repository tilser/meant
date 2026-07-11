package com.meant.api.module.cart.repository;

import com.meant.api.module.cart.entity.Cart;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    @EntityGraph(attributePaths = {"lines"})
    @Query("select cart from Cart cart where cart.id = :id and cart.userId = :userId and cart.active = true")
    Optional<Cart> findWithLinesByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"lines"})
    @Query("select cart from Cart cart where cart.id = :id and cart.userId = :userId and cart.active = true")
    Optional<Cart> findForCheckoutUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
