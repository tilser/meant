package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.ShoppingFilter;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppingFilterRepository extends JpaRepository<ShoppingFilter, String> {

    List<ShoppingFilter> findAllByOrderByDisplayOrderAsc();
}
