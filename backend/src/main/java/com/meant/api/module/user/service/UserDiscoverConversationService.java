package com.meant.api.module.user.service;

import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.DeleteUserDiscoverConversationCommand;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SaveUserDiscoverConversationCommand;
import com.meant.api.module.user.service.dto.UserDiscoverConversationResult;
import com.meant.api.module.user.service.query.ListUserDiscoverConversationsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserDiscoverConversationService {

    private final UserService userService;
    private final UserAssistantConversationPersistenceService conversationPersistenceService;

    @Transactional
    public List<UserDiscoverConversationResult> list(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid ListUserDiscoverConversationsQuery query
    ) {
        validateUser(profileCommand, query.userId());
        userService.ensureProfile(profileCommand);
        return conversationPersistenceService.listDiscover(query.userId(), query.limit());
    }

    @Transactional
    public UserDiscoverConversationResult save(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SaveUserDiscoverConversationCommand command
    ) {
        validateUser(profileCommand, command.userId());
        userService.ensureProfile(profileCommand);
        return conversationPersistenceService.saveDiscover(
                command.userId(),
                command.conversationId(),
                command.title(),
                command.threadJson()
        );
    }

    @Transactional
    public void delete(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid DeleteUserDiscoverConversationCommand command
    ) {
        validateUser(profileCommand, command.userId());
        userService.ensureProfile(profileCommand);
        conversationPersistenceService.deleteDiscover(command.userId(), command.conversationId());
    }

    private void validateUser(EnsureUserProfileCommand profileCommand, UUID userId) {
        if (!profileCommand.id().equals(userId)) {
            throw UserException.forbidden("Discover conversation user does not match authenticated user");
        }
    }
}
