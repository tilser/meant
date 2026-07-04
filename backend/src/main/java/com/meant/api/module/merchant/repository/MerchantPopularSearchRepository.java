package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantPopularSearch;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantPopularSearchRepository extends JpaRepository<MerchantPopularSearch, UUID> {

    List<MerchantPopularSearch> findByMerchant(Merchant merchant);

    @Query("""
            select popularSearch.merchant.id as merchantId,
                   popularSearch.searchText as searchText
            from MerchantPopularSearch popularSearch
            where popularSearch.merchant.id in :merchantIds
            """)
    List<MerchantPopularSearchValue> findValuesByMerchantIdIn(@Param("merchantIds") Collection<UUID> merchantIds);

    @Modifying
    @Query("delete from MerchantPopularSearch popularSearch where popularSearch.merchant = :merchant")
    void deleteByMerchant(Merchant merchant);

    interface MerchantPopularSearchValue {

        UUID getMerchantId();

        String getSearchText();
    }
}
