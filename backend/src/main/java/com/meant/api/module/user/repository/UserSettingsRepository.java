package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
}
