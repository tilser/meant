package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantCategory;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, UUID> {

    List<MerchantCategory> findByMerchant(Merchant merchant);

    @Query("""
            select merchantCategory.merchant.id as merchantId,
                   merchantCategory.name as name
            from MerchantCategory merchantCategory
            where merchantCategory.merchant.id in :merchantIds
            """)
    List<MerchantCategoryValue> findValuesByMerchantIdIn(@Param("merchantIds") Collection<UUID> merchantIds);

    @Modifying
    @Query("delete from MerchantCategory merchantCategory where merchantCategory.merchant = :merchant")
    void deleteByMerchant(Merchant merchant);

    interface MerchantCategoryValue {

        UUID getMerchantId();

        String getName();
    }
}
