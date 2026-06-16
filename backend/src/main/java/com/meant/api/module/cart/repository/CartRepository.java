package com.meant.api.module.cart.repository;

import com.meant.api.module.cart.entity.Cart;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    @EntityGraph(attributePaths = {"lines"})
    @Query("select cart from Cart cart where cart.id = :id")
    Optional<Cart> findWithLinesById(@Param("id") UUID id);
}
