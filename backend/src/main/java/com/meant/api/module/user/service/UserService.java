package com.meant.api.module.user.service;

import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserRepository;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.UpdateUserProfilePictureCommand;
import com.meant.api.module.user.service.command.UpdateUserProfileCommand;
import com.meant.api.module.user.service.query.GetUserQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> PROFILE_PICTURE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final UserRepository userRepository;

    /**
     * Returns the local profile for an authenticated caller, creating it on first sight from the
     * identity carried in their Supabase JWT. On later calls the email is refreshed (it can change
     * in Supabase) while profile names are preserved so user edits are not clobbered.
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
    public User updateProfilePicture(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid UpdateUserProfilePictureCommand updateCommand) {
        if (!profileCommand.id().equals(updateCommand.id())) {
            throw UserException.forbidden("Cannot update another user's profile picture");
        }
        Instant now = Instant.now();
        User user = ensureProfileInternal(profileCommand, now);
        String profilePicturePath = normalizeProfilePicturePath(updateCommand.id(), updateCommand.profilePicturePath());
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
     * row whose email is unchanged, is served by a pure read. Only when the row is missing or the
     * email changed do we serialize provisioning for this user and use the native insert/update.
     */
    private User ensureProfileInternal(EnsureUserProfileCommand command, Instant now) {
        return userRepository.findById(command.id())
                .filter(existing -> existing.getEmail().equals(command.email()))
                .or(() -> provisionProfile(command, now))
                .orElseThrow(() -> UserException.notFound("User not found: " + command.id()));
    }

    private Optional<User> provisionProfile(EnsureUserProfileCommand command, Instant now) {
        userRepository.lockProfileProvisioning(command.id());
        Optional<User> existing = userRepository.findById(command.id())
                .filter(user -> user.getEmail().equals(command.email()));
        if (existing.isPresent()) {
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

    private String normalizeProfilePicturePath(UUID userId, String profilePicturePath) {
        String normalized = profilePicturePath == null ? "" : profilePicturePath.trim();
        String expectedPrefix = userId + "/";
        String fileName = normalized.startsWith(expectedPrefix)
                ? normalized.substring(expectedPrefix.length())
                : "";
        if (!isAllowedProfilePictureFileName(fileName)) {
            throw new UserException("Profile picture path must point to the authenticated user's image object");
        }
        return normalized;
    }

    private boolean isAllowedProfilePictureFileName(String fileName) {
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("..")) {
            return false;
        }
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart <= 0 || extensionStart == fileName.length() - 1) {
            return false;
        }
        String baseName = fileName.substring(0, extensionStart);
        String extension = fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
        return baseName.matches("[A-Za-z0-9._-]+") && PROFILE_PICTURE_EXTENSIONS.contains(extension);
    }
}
