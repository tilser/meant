package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.MerchantRaw;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRawRepository extends JpaRepository<MerchantRaw, UUID> {
}
