package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserShoppingFilter;
import com.meant.api.module.user.entity.UserShoppingFilterId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserShoppingFilterRepository extends JpaRepository<UserShoppingFilter, UserShoppingFilterId> {

    List<UserShoppingFilter> findByIdUserId(UUID userId);

    void deleteByIdUserId(UUID userId);
}
