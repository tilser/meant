package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantService;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MerchantServiceRepository extends JpaRepository<MerchantService, UUID> {

    @Modifying
    @Query("delete from MerchantService merchantService where merchantService.merchant = :merchant")
    void deleteByMerchant(Merchant merchant);
}
