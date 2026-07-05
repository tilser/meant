package com.meant.api.module.user.service;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.module.user.constant.UserAssistantMessageRole;
import com.meant.api.module.user.entity.UserAssistantConversation;
import com.meant.api.module.user.entity.UserAssistantMessage;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserAssistantConversationRepository;
import com.meant.api.module.user.repository.UserAssistantMessageRepository;
import com.meant.api.module.user.service.dto.UserAssistantConversationResult;
import com.meant.api.module.user.service.dto.UserAssistantConversationSummaryResult;
import com.meant.api.module.user.service.dto.UserAssistantMessageResult;
import com.meant.api.module.user.service.dto.UserAssistantPageContext;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserAssistantConversationPersistenceService {

    private static final int PROMPT_HISTORY_LIMIT = 12;
    private static final int RESTORE_HISTORY_LIMIT = 50;
    private static final TypeReference<List<UserProductSearchProductResult>> PRODUCT_LIST_TYPE =
            new TypeReference<>() {
            };

    private final UserAssistantConversationRepository conversationRepository;
    private final UserAssistantMessageRepository messageRepository;
    private final OpenRouterProperties openRouterProperties;
    private final ObjectMapper objectMapper;

    public UserAssistantConversationResult latest(UUID userId) {
        return conversationRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId)
                .map(conversation -> conversationResult(conversation, userId))
                .orElseGet(() -> new UserAssistantConversationResult(null, null, null, null, List.of()));
    }

    public List<UserAssistantConversationSummaryResult> list(UUID userId, int limit) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(
                        userId,
                        PageRequest.of(0, limit))
                .stream()
                .map(this::conversationSummary)
                .toList();
    }

    public UserAssistantConversationResult get(UUID userId, UUID conversationId) {
        UserAssistantConversation conversation = conversationRepository
                .findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> UserException.notFound("Assistant conversation not found"));
        return conversationResult(conversation, userId);
    }

    public UserAssistantConversation saveUserMessage(
            UUID userId,
            UUID conversationId,
            String userMessage,
            String pageContextJson,
            Instant now
    ) {
        UserAssistantConversation conversation = conversation(userId, conversationId, userMessage, now);
        messageRepository.save(UserAssistantMessage.create(
                conversation.getId(),
                userId,
                UserAssistantMessageRole.USER,
                userMessage,
                null,
                pageContextJson,
                null,
                now
        ));
        return conversation;
    }

    public UserAssistantMessage saveAssistantMessage(
            UUID conversationId,
            UUID userId,
            String assistantText,
            String pageContextJson,
            List<UserProductSearchProductResult> products
    ) {
        return messageRepository.save(UserAssistantMessage.create(
                conversationId,
                userId,
                UserAssistantMessageRole.ASSISTANT,
                assistantText,
                openRouterProperties.models().chatModel(),
                pageContextJson,
                productsJson(products),
                Instant.now()
        ));
    }

    public void touch(UserAssistantConversation conversation) {
        conversation.touch(Instant.now());
        conversationRepository.save(conversation);
    }

    public List<UserAssistantMessage> promptHistory(UUID conversationId, UUID userId) {
        List<UserAssistantMessage> messages = new ArrayList<>(recentMessages(
                conversationId,
                userId,
                PROMPT_HISTORY_LIMIT
        ));
        Collections.reverse(messages);
        return messages.stream().limit(PROMPT_HISTORY_LIMIT).toList();
    }

    public String pageContextJson(UserAssistantPageContext pageContext) {
        if (pageContext == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(pageContext);
        } catch (JacksonException exception) {
            log.warn("Failed to serialize assistant page context", exception);
            return null;
        }
    }

    private UserAssistantConversationResult conversationResult(
            UserAssistantConversation conversation,
            UUID userId
    ) {
        return new UserAssistantConversationResult(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                restoreMessages(conversation.getId(), userId));
    }

    private UserAssistantConversationSummaryResult conversationSummary(UserAssistantConversation conversation) {
        return new UserAssistantConversationSummaryResult(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }

    private UserAssistantConversation conversation(
            UUID userId,
            UUID conversationId,
            String userMessage,
            Instant now
    ) {
        if (conversationId == null) {
            return conversationRepository.save(UserAssistantConversation.create(
                    userId,
                    title(userMessage),
                    now
            ));
        }
        UserAssistantConversation conversation = conversationRepository
                .findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> UserException.notFound("Assistant conversation not found"));
        conversation.touch(now);
        return conversationRepository.save(conversation);
    }

    private List<UserAssistantMessageResult> restoreMessages(UUID conversationId, UUID userId) {
        List<UserAssistantMessage> messages = new ArrayList<>(recentMessages(
                conversationId,
                userId,
                RESTORE_HISTORY_LIMIT
        ));
        Collections.reverse(messages);
        return messages.stream()
                .limit(RESTORE_HISTORY_LIMIT)
                .map(message -> new UserAssistantMessageResult(
                        message.getId(),
                        message.getRole(),
                        message.getContent(),
                        products(message.getProductsJson()),
                        message.getCreatedAt()))
                .toList();
    }

    private List<UserAssistantMessage> recentMessages(UUID conversationId, UUID userId, int limit) {
        return messageRepository.findByConversationIdAndUserIdOrderByCreatedAtDesc(
                conversationId,
                userId,
                PageRequest.of(0, limit)
        );
    }

    private String title(String message) {
        String normalized = message.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 77) + "...";
    }

    private String productsJson(List<UserProductSearchProductResult> products) {
        if (products.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(products);
        } catch (JacksonException exception) {
            log.warn("Failed to serialize assistant products", exception);
            return null;
        }
    }

    private List<UserProductSearchProductResult> products(String productsJson) {
        if (productsJson == null || productsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(productsJson, PRODUCT_LIST_TYPE);
        } catch (JacksonException exception) {
            log.warn("Failed to deserialize assistant products", exception);
            return List.of();
        }
    }
}
