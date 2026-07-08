package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantCapabilityExtension;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MerchantCapabilityExtensionRepository extends JpaRepository<MerchantCapabilityExtension, UUID> {

    @Modifying
    @Query("""
            delete from MerchantCapabilityExtension extension
            where extension.merchantCapability.merchant = :merchant
            """)
    void deleteByMerchant(Merchant merchant);

    List<MerchantCapabilityExtension> findByMerchantCapabilityId(UUID merchantCapabilityId);
}
