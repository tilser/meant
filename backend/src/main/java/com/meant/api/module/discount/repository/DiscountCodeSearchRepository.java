package com.meant.api.module.discount.repository;

import com.meant.api.module.discount.entity.DiscountCodeSearch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscountCodeSearchRepository extends JpaRepository<DiscountCodeSearch, UUID> {
}
