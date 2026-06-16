package com.meant.api.module.merchant.repository;

import com.meant.api.module.merchant.entity.MerchantCartLine;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantCartLineRepository extends JpaRepository<MerchantCartLine, UUID> {
}
