package com.meant.api.module.user.service;

import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserRepository;
import com.meant.api.module.user.service.command.UpdateUserProfileCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.query.GetUserQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Returns the local profile for an authenticated caller, creating it on first sight from the
     * identity carried in their Supabase JWT. On later calls the email is refreshed (it can change
     * in Supabase) while profile names are preserved so user edits are not clobbered.
     */
    @Transactional
    public User upsert(@NotNull @Valid UpsertUserCommand command) {
        return upsertInternal(command, Instant.now());
    }

    @Transactional(readOnly = true)
    public User get(@NotNull @Valid GetUserQuery query) {
        return findUser(query.id());
    }

    /**
     * Ensures the profile exists (upsert from the JWT identity) and applies the user's name edits in a
     * single transaction. A client may PATCH before ever calling GET /me, so the row must be created
     * here if missing; combining both steps keeps the operation atomic and avoids a second roundtrip.
     */
    @Transactional
    public User updateProfile(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid UpdateUserProfileCommand updateCommand) {
        Instant now = Instant.now();
        User user = upsertInternal(upsertCommand, now);
        user.updateProfile(updateCommand.firstName(), updateCommand.surname(), now);
        return user;
    }

    private User upsertInternal(UpsertUserCommand command, Instant now) {
        return userRepository.findById(command.id())
                .map(existing -> {
                    existing.updateEmail(command.email(), now);
                    return existing;
                })
                .orElseGet(() -> createUser(command, now));
    }

    /**
     * Inserts a fresh profile row. Because upsert runs on read, two concurrent first-time requests can
     * both miss the existing row and race to insert; the loser hits a unique-constraint violation. We
     * recover by re-reading the row the winner committed and refreshing its email.
     */
    private User createUser(UpsertUserCommand command, Instant now) {
        try {
            return userRepository.saveAndFlush(User.builder()
                    .id(command.id())
                    .email(command.email())
                    .firstName(command.firstName())
                    .surname(command.surname())
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        } catch (DataIntegrityViolationException raceLost) {
            User existing = userRepository.findById(command.id())
                    .orElseThrow(() -> raceLost);
            existing.updateEmail(command.email(), now);
            return existing;
        }
    }

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserException("User not found: " + id));
    }
}
