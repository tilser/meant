package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantPaymentHandler;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MerchantPaymentHandlerRepository extends JpaRepository<MerchantPaymentHandler, UUID> {

    @Modifying
    @Query("delete from MerchantPaymentHandler paymentHandler where paymentHandler.merchant = :merchant")
    void deleteByMerchant(Merchant merchant);
}
