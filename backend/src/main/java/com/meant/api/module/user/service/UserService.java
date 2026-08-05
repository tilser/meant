package com.meant.api.module.user.service;

import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserRepository;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.UpdateUserNewsletterCommand;
import com.meant.api.module.user.service.command.UpdateUserProfilePictureCommand;
import com.meant.api.module.user.service.command.UpdateUserProfileCommand;
import com.meant.api.module.user.service.query.GetUserQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Returns the local profile for an authenticated caller, creating it on first sight from the
     * identity carried in their Supabase JWT. On later calls the email is refreshed (it can change
     * in Supabase) while profile names are preserved so user edits are not clobbered. Historical
     * profiles with no name are backfilled from the identity when it becomes available.
     */
    @Transactional
    public User ensureProfile(@NotNull @Valid EnsureUserProfileCommand command) {
        return ensureProfileInternal(command, Instant.now());
    }

    @Transactional(readOnly = true)
    public User get(@NotNull @Valid GetUserQuery query) {
        return findUser(query.id());
    }

    /**
     * Ensures the profile exists from the JWT identity and applies the user's name edits in a single
     * transaction. A client may PATCH before ever calling GET /me, so the row must be created here if
     * missing; combining both steps keeps the operation atomic and avoids a second roundtrip.
     */
    @Transactional
    public User updateProfile(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid UpdateUserProfileCommand updateCommand) {
        Instant now = Instant.now();
        User user = ensureProfileInternal(profileCommand, now);
        user.updateProfile(updateCommand.firstName(), updateCommand.surname(), now);
        return user;
    }

    @Transactional
    public User updateNewsletter(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid UpdateUserNewsletterCommand updateCommand) {
        if (!profileCommand.id().equals(updateCommand.id())) {
            throw UserException.forbidden("Cannot update another user's newsletter subscription");
        }
        Instant now = Instant.now();
        User user = ensureProfileInternal(profileCommand, now);
        user.updateNewsletter(Boolean.TRUE.equals(updateCommand.newsletter()), now);
        return user;
    }

    @Transactional
    public User updateProfilePicture(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid UpdateUserProfilePictureCommand updateCommand) {
        if (!profileCommand.id().equals(updateCommand.id())) {
            throw UserException.forbidden("Cannot update another user's profile picture");
        }
        Instant now = Instant.now();
        User user = ensureProfileInternal(profileCommand, now);
        String profilePicturePath = UserOwnedImagePathValidator.normalize(
                updateCommand.id(), updateCommand.profilePicturePath(), "Profile picture path");
        user.updateProfilePicture(profilePicturePath, now);
        return user;
    }

    @Transactional
    public User removeProfilePicture(@NotNull @Valid EnsureUserProfileCommand profileCommand) {
        Instant now = Instant.now();
        User user = ensureProfileInternal(profileCommand, now);
        user.updateProfilePicture(null, now);
        return user;
    }

    /**
     * Resolves the managed profile entity, writing only when necessary. The common case, an existing
     * row whose identity details are unchanged, is served by a pure read. A missing row, changed
     * email, or newly available name serializes provisioning for this user before writing.
     */
    private User ensureProfileInternal(EnsureUserProfileCommand command, Instant now) {
        return userRepository.findById(command.id())
                .filter(existing -> (command.email() == null
                        || Objects.equals(existing.getEmail(), command.email()))
                        && (StringUtils.hasText(existing.getFirstName())
                                || !StringUtils.hasText(command.firstName())))
                .or(() -> provisionProfile(command, now))
                .orElseThrow(() -> UserException.notFound("User not found: " + command.id()));
    }

    private Optional<User> provisionProfile(EnsureUserProfileCommand command, Instant now) {
        userRepository.lockProfileProvisioning(command.id());
        Optional<User> existing = userRepository.findById(command.id());
        if (existing.isPresent()) {
            User user = existing.orElseThrow();
            if (command.email() != null) {
                user.updateEmail(command.email(), now);
            }
            if (!StringUtils.hasText(user.getFirstName()) && StringUtils.hasText(command.firstName())) {
                user.updateProfile(command.firstName(), command.surname(), now);
            }
            return existing;
        }
        userRepository.insertOrRefreshFromIdentity(
                command.id(),
                command.email(),
                command.firstName(),
                command.surname(),
                now);
        return Optional.of(findUser(command.id()));
    }

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> UserException.notFound("User not found: " + id));
    }

}
