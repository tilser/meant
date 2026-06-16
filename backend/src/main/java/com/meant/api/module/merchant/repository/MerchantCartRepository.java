package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.MerchantCart;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantCartRepository extends JpaRepository<MerchantCart, UUID> {

    @EntityGraph(attributePaths = {"merchant", "lines"})
    @Query("select cart from MerchantCart cart where cart.id = :id")
    Optional<MerchantCart> findWithMerchantAndLinesById(@Param("id") UUID id);
}
