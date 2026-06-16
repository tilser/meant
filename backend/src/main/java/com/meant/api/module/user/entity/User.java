package com.meant.api.module.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        }
)
public class User {

    /**
     * Matches the Supabase {@code auth.users.id} (the JWT {@code sub} claim). Never generated locally.
     */
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String email;

    private String firstName;

    private String surname;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Refreshes the email only when it actually changed. This matters because the email is refreshed
     * on every {@code GET /api/users/me} (upsert-on-read); without the guard Hibernate would flush a
     * redundant {@code UPDATE} on every read request.
     */
    public void updateEmail(String email, Instant now) {
        if (!Objects.equals(this.email, email)) {
            this.email = email;
            this.updatedAt = now;
        }
    }

    public void updateProfile(String firstName, String surname, Instant now) {
        if (Objects.equals(this.firstName, firstName) && Objects.equals(this.surname, surname)) {
            return;
        }
        this.firstName = firstName;
        this.surname = surname;
        this.updatedAt = now;
    }
}
