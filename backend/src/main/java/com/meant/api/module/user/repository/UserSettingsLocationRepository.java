package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserSettingsLocation;
import com.meant.api.module.user.entity.UserSettingsLocationId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsLocationRepository extends JpaRepository<UserSettingsLocation, UserSettingsLocationId> {

    List<UserSettingsLocation> findByIdUserIdOrderByDisplayOrderAsc(UUID userId);

    void deleteByIdUserId(UUID userId);
}
