package com.meant.api.module.user.service;

import com.meant.api.module.user.entity.UserAssistantConversation;
import com.meant.api.module.user.entity.UserAssistantMessage;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.command.SendUserAssistantMessageCommand;
import com.meant.api.module.user.service.dto.UserAssistantConversationResult;
import com.meant.api.module.user.service.dto.UserAssistantConversationSummaryResult;
import com.meant.api.module.user.service.dto.UserAssistantRoute;
import com.meant.api.module.user.service.dto.UserAssistantStreamEvent;
import com.meant.api.module.user.service.dto.UserAssistantToolContext;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserProductSearchResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GetLatestUserAssistantConversationQuery;
import com.meant.api.module.user.service.query.GetUserAssistantConversationQuery;
import com.meant.api.module.user.service.query.ListUserAssistantConversationsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class UserAssistantChatService {

    private static final int PRODUCT_RESPONSE_LIMIT = 4;

    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final UserProductSearchService userProductSearchService;
    private final UserAssistantConversationPersistenceService conversationPersistenceService;
    private final UserAssistantRouteClassifier routeClassifier;
    private final UserAssistantPromptContextBuilder promptContextBuilder;
    private final UserAssistantResponseGenerator responseGenerator;

    @Transactional
    public UserAssistantConversationResult latest(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid GetLatestUserAssistantConversationQuery query
    ) {
        validateUser(profileCommand, query.userId());
        userService.ensureProfile(profileCommand);
        return conversationPersistenceService.latest(query.userId());
    }

    @Transactional
    public List<UserAssistantConversationSummaryResult> list(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid ListUserAssistantConversationsQuery query
    ) {
        validateUser(profileCommand, query.userId());
        userService.ensureProfile(profileCommand);
        return conversationPersistenceService.list(query.userId(), query.limit());
    }

    @Transactional
    public UserAssistantConversationResult get(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid GetUserAssistantConversationQuery query
    ) {
        validateUser(profileCommand, query.userId());
        userService.ensureProfile(profileCommand);
        return conversationPersistenceService.get(query.userId(), query.conversationId());
    }

    public void stream(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SendUserAssistantMessageCommand command,
            Consumer<UserAssistantStreamEvent> eventConsumer
    ) {
        validateUser(profileCommand, command.userId());
        userService.ensureProfile(profileCommand);

        Instant now = Instant.now();
        String userMessage = command.message().trim();
        String pageContextJson = conversationPersistenceService.pageContextJson(command.pageContext());
        UserAssistantConversation conversation = conversationPersistenceService.saveUserMessage(
                command.userId(),
                command.conversationId(),
                userMessage,
                pageContextJson,
                now
        );
        eventConsumer.accept(UserAssistantStreamEvent.metadata(conversation.getId()));

        UserSettingsResult settings = userSettingsService.get(profileCommand);
        List<UserAssistantMessage> history = conversationPersistenceService.promptHistory(
                conversation.getId(),
                command.userId()
        );
        UserAssistantRoute route = routeClassifier.route(userMessage, settings, command.pageContext());
        UserAssistantToolContext toolContext = promptContextBuilder.toolContext(profileCommand, command, route);
        if (route.isSearch() && promptContextBuilder.shouldAnswerFromSavedProducts(userMessage, command.pageContext())) {
            route = route.asAnswer();
        }

        List<UserProductSearchProductResult> products = List.of();
        String searchError = null;
        if (route.isSearch()) {
            try {
                UserProductSearchResult searchResult = userProductSearchService.search(
                        profileCommand,
                        new SearchUserProductsCommand(command.userId(), route.searchQuery(), null)
                );
                products = searchResult.products().stream()
                        .limit(PRODUCT_RESPONSE_LIMIT)
                        .toList();
            } catch (RuntimeException exception) {
                searchError = "Product search is temporarily unavailable.";
                log.warn("Failed to run assistant product search", exception);
            }
        }

        // If upstream streaming fails, persist a local fallback so the saved user turn has an assistant reply.
        String assistantText = responseGenerator.answer(
                route,
                userMessage,
                settings,
                command.pageContext(),
                toolContext,
                history,
                products,
                searchError,
                eventConsumer);
        UserAssistantMessage assistantMessage = conversationPersistenceService.saveAssistantMessage(
                conversation.getId(),
                command.userId(),
                assistantText,
                pageContextJson,
                products
        );
        conversationPersistenceService.touch(conversation);
        eventConsumer.accept(UserAssistantStreamEvent.done(
                conversation.getId(),
                assistantMessage.getId(),
                assistantText,
                products
        ));
    }

    private void validateUser(EnsureUserProfileCommand profileCommand, UUID userId) {
        if (!profileCommand.id().equals(userId)) {
            throw UserException.forbidden("Assistant user does not match authenticated user");
        }
    }
}
