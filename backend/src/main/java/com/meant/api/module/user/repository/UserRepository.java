package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    /**
     * Atomically inserts the profile or, if it already exists, refreshes its email — leaving the
     * user-edited names untouched. Performed as a single {@code INSERT ... ON CONFLICT} so concurrent
     * first requests never collide on the unique constraint. {@code updated_at} only advances when
     * the email actually changes, mirroring the entity's dirty-check guard so reads stay write-free.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO users (id, email, first_name, surname, created_at, updated_at)
            VALUES (:id, :email, :firstName, :surname, :now, :now)
            ON CONFLICT (id) DO UPDATE
                SET email = EXCLUDED.email,
                    updated_at = CASE WHEN users.email IS DISTINCT FROM EXCLUDED.email
                                      THEN EXCLUDED.updated_at ELSE users.updated_at END
            """, nativeQuery = true)
    void insertOrRefreshFromIdentity(
            @Param("id") UUID id,
            @Param("email") String email,
            @Param("firstName") String firstName,
            @Param("surname") String surname,
            @Param("now") Instant now);
}
