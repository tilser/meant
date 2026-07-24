package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserConversationKind;
import com.meant.api.module.user.entity.UserDiscoverConversation;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserDiscoverConversationRepository;
import com.meant.api.module.user.entity.UserDiscoverConversationTombstone;
import com.meant.api.module.user.repository.UserDiscoverConversationTombstoneRepository;
import com.meant.api.module.user.service.command.DeleteUserDiscoverConversationCommand;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SaveUserDiscoverConversationCommand;
import com.meant.api.module.user.service.dto.UserDiscoverConversationResult;
import com.meant.api.module.user.service.query.GetUserDiscoverConversationQuery;
import com.meant.api.module.user.service.query.ListUserDiscoverConversationsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserDiscoverConversationService {

    private final UserService userService;
    private final UserDiscoverConversationRepository conversationRepository;
    private final UserDiscoverConversationTombstoneRepository tombstoneRepository;
    private final UserDiscoverConversationSnapshotSanitizer snapshotSanitizer;

    @Transactional(readOnly = true)
    public void requireOwned(@NotNull @Valid GetUserDiscoverConversationQuery query) {
        ownedConversation(query);
    }

    @Transactional(readOnly = true)
    public UserDiscoverConversationResult get(@NotNull @Valid GetUserDiscoverConversationQuery query) {
        return result(ownedConversation(query));
    }

    @Transactional
    public List<UserDiscoverConversationResult> list(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid ListUserDiscoverConversationsQuery query
    ) {
        validateUser(profileCommand, query.userId());
        userService.ensureProfile(profileCommand);
        return conversationRepository.findByUserIdAndKindOrderByUpdatedAtDesc(
                        query.userId(),
                        UserConversationKind.DISCOVER.name(),
                        PageRequest.of(0, query.limit()))
                .stream()
                .map(this::result)
                .toList();
    }

    @Transactional
    public UserDiscoverConversationResult save(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SaveUserDiscoverConversationCommand command
    ) {
        validateUser(profileCommand, command.userId());
        userService.ensureProfile(profileCommand);
        conversationRepository.lockId(command.conversationId());
        rejectDeleted(command.conversationId());
        Instant now = persistenceTimestamp();
        var sanitizedSnapshot = snapshotSanitizer.sanitize(title(command.title()), command.threadJson());
        String title = title(sanitizedSnapshot.title());
        String threadJson = sanitizedSnapshot.threadJson();
        UserDiscoverConversation conversation = conversationRepository.findByIdForUpdate(command.conversationId())
                .map(existing -> update(existing, command, title, threadJson, now))
                .orElseGet(() -> create(command, title, threadJson, now));
        return result(conversationRepository.save(conversation));
    }

    @Transactional
    public void delete(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid DeleteUserDiscoverConversationCommand command
    ) {
        validateUser(profileCommand, command.userId());
        userService.ensureProfile(profileCommand);
        conversationRepository.lockId(command.conversationId());
        UserDiscoverConversation conversation = conversationRepository.findByIdForUpdate(command.conversationId())
                .filter(existing -> existing.getUserId().equals(command.userId()))
                .filter(existing -> UserConversationKind.DISCOVER.name().equals(existing.getKind()))
                .orElseThrow(() -> UserException.notFound("Discover conversation not found"));
        tombstoneRepository.save(UserDiscoverConversationTombstone.create(
                conversation.getId(), conversation.getUserId(), persistenceTimestamp()));
        conversationRepository.delete(conversation);
    }

    private void validateUser(EnsureUserProfileCommand profileCommand, UUID userId) {
        if (!profileCommand.id().equals(userId)) {
            throw UserException.forbidden("Discover conversation user does not match authenticated user");
        }
    }

    private UserDiscoverConversation ownedConversation(GetUserDiscoverConversationQuery query) {
        return conversationRepository.findByIdAndUserIdAndKind(
                        query.conversationId(),
                        query.userId(),
                        UserConversationKind.DISCOVER.name())
                .orElseThrow(() -> UserException.notFound("Discover conversation not found"));
    }

    private UserDiscoverConversation update(
            UserDiscoverConversation conversation,
            SaveUserDiscoverConversationCommand command,
            String title,
            String threadJson,
            Instant now
    ) {
        if (!conversation.getUserId().equals(command.userId())
                || !UserConversationKind.DISCOVER.name().equals(conversation.getKind())) {
            throw UserException.notFound("Discover conversation not found");
        }
        if (command.expectedRevision() == null || command.expectedRevision() != conversation.getRevision()) {
            throw UserException.conflict("Discover conversation changed before it could be saved");
        }
        conversation.replaceSnapshot(title, threadJson, now);
        return conversation;
    }

    private UserDiscoverConversation create(
            SaveUserDiscoverConversationCommand command,
            String title,
            String threadJson,
            Instant now
    ) {
        rejectDeleted(command.conversationId());
        if (command.expectedRevision() != null) {
            throw UserException.conflict("Discover conversation changed before it could be saved");
        }
        return UserDiscoverConversation.create(
                command.conversationId(), command.userId(), title, threadJson, now);
    }

    private void rejectDeleted(UUID conversationId) {
        if (tombstoneRepository.existsById(conversationId)) {
            throw UserException.conflict("Deleted Discover conversation cannot be recreated");
        }
    }

    private UserDiscoverConversationResult result(UserDiscoverConversation conversation) {
        return new UserDiscoverConversationResult(
                conversation.getId(),
                snapshotSanitizer.sanitizeTitle(conversation.getTitle()),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                snapshotSanitizer.sanitize(conversation.getPayload()),
                conversation.getRevision()
        );
    }

    private String title(String value) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 77) + "...";
    }

    private Instant persistenceTimestamp() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
