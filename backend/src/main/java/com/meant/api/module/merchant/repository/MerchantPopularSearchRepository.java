package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantPopularSearch;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MerchantPopularSearchRepository extends JpaRepository<MerchantPopularSearch, UUID> {

    List<MerchantPopularSearch> findByMerchant(Merchant merchant);

    @Modifying
    @Query("delete from MerchantPopularSearch popularSearch where popularSearch.merchant = :merchant")
    void deleteByMerchant(Merchant merchant);
}
