package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantCapabilityRequirement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MerchantCapabilityRequirementRepository extends JpaRepository<MerchantCapabilityRequirement, UUID> {

    @Modifying
    @Query("""
            delete from MerchantCapabilityRequirement requirement
            where requirement.merchantCapability.merchant = :merchant
            """)
    void deleteByMerchant(Merchant merchant);
}
