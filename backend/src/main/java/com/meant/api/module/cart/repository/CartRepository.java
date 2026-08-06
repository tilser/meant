package com.meant.api.module.cart.repository;

import com.meant.api.module.cart.entity.Cart;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    @Query("""
            select cart from Cart cart
            where cart.userId = :userId
              and cart.active = true
              and (cart.expiresAt is null or cart.expiresAt > :now)
            order by cart.updatedAt desc, cart.id asc
            """)
    List<Cart> findActiveForUser(
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select cart from Cart cart
            where cart.userId = :userId
              and cart.active = true
              and (cart.expiresAt is null or cart.expiresAt > :now)
            order by cart.updatedAt desc, cart.id asc
            """)
    List<Cart> findActiveForOwnershipTransfer(
            @Param("userId") UUID userId,
            @Param("now") Instant now
    );

    @Query("""
            select cart from Cart cart
            where cart.userId = :userId
              and cart.routingScopeKey = :routingScopeKey
              and cart.active = true
              and (cart.expiresAt is null or cart.expiresAt > :now)
            order by cart.updatedAt desc, cart.id asc
            """)
    List<Cart> findActiveForRoutingScope(
            @Param("userId") UUID userId,
            @Param("routingScopeKey") String routingScopeKey,
            @Param("now") Instant now,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"lines"})
    @Query("select cart from Cart cart where cart.id = :id and cart.userId = :userId and cart.active = true")
    Optional<Cart> findWithLinesByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"lines"})
    @Query("select cart from Cart cart where cart.id = :id and cart.userId = :userId and cart.active = true")
    Optional<Cart> findForCheckoutUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"lines"})
    @Query("select cart from Cart cart where cart.remoteCartIdHash = :remoteCartIdHash")
    Optional<Cart> findForRemoteIdentityReconciliation(
            @Param("remoteCartIdHash") String remoteCartIdHash
    );
}
