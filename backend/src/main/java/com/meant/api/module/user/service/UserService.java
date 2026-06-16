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
import lombok.RequiredArgsConstructor;
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
        Instant now = Instant.now();
        return userRepository.findById(command.id())
                .map(existing -> {
                    existing.updateEmail(command.email(), now);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .id(command.id())
                        .email(command.email())
                        .firstName(command.firstName())
                        .surname(command.surname())
                        .createdAt(now)
                        .updatedAt(now)
                        .build()));
    }

    @Transactional(readOnly = true)
    public User get(@NotNull @Valid GetUserQuery query) {
        return findUser(query.id());
    }

    @Transactional
    public User updateProfile(@NotNull @Valid UpdateUserProfileCommand command) {
        User user = findUser(command.id());
        user.updateProfile(command.firstName(), command.surname(), Instant.now());
        return user;
    }

    private User findUser(java.util.UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserException("User not found: " + id));
    }
}
