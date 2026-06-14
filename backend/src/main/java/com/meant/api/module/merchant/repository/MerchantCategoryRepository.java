package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, UUID> {

    List<MerchantCategory> findByMerchant(Merchant merchant);

    @Modifying
    @Query("delete from MerchantCategory merchantCategory where merchantCategory.merchant = :merchant")
    void deleteByMerchant(Merchant merchant);
}
