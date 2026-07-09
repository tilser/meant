package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantCapability;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MerchantCapabilityRepository extends JpaRepository<MerchantCapability, UUID> {

    @Modifying
    @Query("delete from MerchantCapability merchantCapability where merchantCapability.merchant = :merchant")
    void deleteByMerchant(Merchant merchant);

    @Query("""
            select count(merchantCapability) > 0 from MerchantCapability merchantCapability
            where merchantCapability.merchant.id = :merchantId and merchantCapability.name = :name
            """)
    boolean existsByMerchantIdAndName(UUID merchantId, String name);
}
