package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserSettings;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO user_settings (user_id, budget, currency, created_at, updated_at)
            VALUES (:userId, :budget, :currency, :now, :now)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int insertDefaultIfMissing(
            @Param("userId") UUID userId,
            @Param("budget") Integer budget,
            @Param("currency") String currency,
            @Param("now") Instant now
    );
}
