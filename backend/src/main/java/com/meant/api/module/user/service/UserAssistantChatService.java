package com.meant.api.module.user.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.OpenRouterJsonExtractor;
import com.meant.api.common.service.dto.OpenRouterChatMessage;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.constant.UserAssistantMessageRole;
import com.meant.api.module.user.entity.UserAssistantConversation;
import com.meant.api.module.user.entity.UserAssistantMessage;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserAssistantConversationRepository;
import com.meant.api.module.user.repository.UserAssistantMessageRepository;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.command.SendUserAssistantMessageCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserAssistantConversationResult;
import com.meant.api.module.user.service.dto.UserAssistantConversationSummaryResult;
import com.meant.api.module.user.service.dto.UserAssistantMessageResult;
import com.meant.api.module.user.service.dto.UserAssistantPageContext;
import com.meant.api.module.user.service.dto.UserAssistantStreamEvent;
import com.meant.api.module.user.service.dto.UserAssistantToolContext;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserProductSearchResult;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GetLatestUserAssistantConversationQuery;
import com.meant.api.module.user.service.query.GetUserAssistantConversationQuery;
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
import com.meant.api.module.user.service.query.ListUserAssistantConversationsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class UserAssistantChatService {

    private static final int PROMPT_HISTORY_LIMIT = 12;
    private static final int RESTORE_HISTORY_LIMIT = 50;
    private static final int PRODUCT_RESPONSE_LIMIT = 4;
    private static final TypeReference<List<UserProductSearchProductResult>> PRODUCT_LIST_TYPE =
            new TypeReference<>() {
            };

    private static final String ACTION_ANSWER = "answer";
    private static final String ACTION_SEARCH = "search_products";
    private static final String ACTION_CLARIFY = "clarify";

    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final UserProductSearchService userProductSearchService;
    private final UserSavedProductService userSavedProductService;
    private final UserAssistantConversationRepository conversationRepository;
    private final UserAssistantMessageRepository messageRepository;
    private final OpenRouterChatClient openRouterChatClient;
    private final OpenRouterProperties openRouterProperties;
    private final ObjectMapper objectMapper;

    @Transactional
    public UserAssistantConversationResult latest(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid GetLatestUserAssistantConversationQuery query
    ) {
        validateUser(upsertCommand, query.userId());
        userService.upsert(upsertCommand);
        return conversationRepository.findFirstByUserIdOrderByUpdatedAtDesc(query.userId())
                .map(conversation -> conversationResult(conversation, query.userId()))
                .orElseGet(() -> new UserAssistantConversationResult(null, null, null, null, List.of()));
    }

    @Transactional
    public List<UserAssistantConversationSummaryResult> list(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid ListUserAssistantConversationsQuery query
    ) {
        validateUser(upsertCommand, query.userId());
        userService.upsert(upsertCommand);
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(
                        query.userId(),
                        PageRequest.of(0, query.limit()))
                .stream()
                .map(this::conversationSummary)
                .toList();
    }

    @Transactional
    public UserAssistantConversationResult get(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid GetUserAssistantConversationQuery query
    ) {
        validateUser(upsertCommand, query.userId());
        userService.upsert(upsertCommand);
        UserAssistantConversation conversation = conversationRepository
                .findByIdAndUserId(query.conversationId(), query.userId())
                .orElseThrow(() -> UserException.notFound("Assistant conversation not found"));
        return conversationResult(conversation, query.userId());
    }

    public void stream(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid SendUserAssistantMessageCommand command,
            Consumer<UserAssistantStreamEvent> eventConsumer
    ) {
        validateUser(upsertCommand, command.userId());
        userService.upsert(upsertCommand);

        Instant now = Instant.now();
        String userMessage = command.message().trim();
        String pageContextJson = pageContextJson(command.pageContext());
        UserAssistantConversation conversation = conversation(command, userMessage, now);
        messageRepository.save(UserAssistantMessage.create(
                conversation.getId(),
                command.userId(),
                UserAssistantMessageRole.USER,
                userMessage,
                null,
                pageContextJson,
                null,
                now
        ));
        eventConsumer.accept(UserAssistantStreamEvent.metadata(conversation.getId()));

        UserSettingsResult settings = userSettingsService.get(upsertCommand);
        List<UserAssistantMessage> history = promptHistory(conversation.getId(), command.userId());
        AssistantRoute route = route(userMessage, settings, command.pageContext());
        UserAssistantToolContext toolContext = toolContext(upsertCommand, command, route);
        if (route.isSearch() && shouldAnswerFromSavedProducts(userMessage, command.pageContext())) {
            route = route.asAnswer();
        }

        List<UserProductSearchProductResult> products = List.of();
        String searchError = null;
        if (route.isSearch()) {
            try {
                UserProductSearchResult searchResult = userProductSearchService.search(
                        upsertCommand,
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

        String assistantText = answer(
                route,
                userMessage,
                settings,
                command.pageContext(),
                toolContext,
                history,
                products,
                searchError,
                eventConsumer);
        UserAssistantMessage assistantMessage = messageRepository.save(UserAssistantMessage.create(
                conversation.getId(),
                command.userId(),
                UserAssistantMessageRole.ASSISTANT,
                assistantText,
                openRouterProperties.models().chatModel(),
                pageContextJson,
                productsJson(products),
                Instant.now()
        ));
        conversation.touch(Instant.now());
        conversationRepository.save(conversation);
        eventConsumer.accept(UserAssistantStreamEvent.done(
                conversation.getId(),
                assistantMessage.getId(),
                assistantText,
                products
        ));
    }

    private void validateUser(UpsertUserCommand upsertCommand, UUID userId) {
        if (!upsertCommand.id().equals(userId)) {
            throw UserException.forbidden("Assistant user does not match authenticated user");
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
            SendUserAssistantMessageCommand command,
            String userMessage,
            Instant now
    ) {
        if (command.conversationId() == null) {
            return conversationRepository.save(UserAssistantConversation.create(
                    command.userId(),
                    title(userMessage),
                    now
            ));
        }
        UserAssistantConversation conversation = conversationRepository
                .findByIdAndUserId(command.conversationId(), command.userId())
                .orElseThrow(() -> UserException.notFound("Assistant conversation not found"));
        conversation.touch(now);
        return conversationRepository.save(conversation);
    }

    private List<UserAssistantMessageResult> restoreMessages(UUID conversationId, UUID userId) {
        List<UserAssistantMessage> messages = recentMessages(conversationId, userId, RESTORE_HISTORY_LIMIT);
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

    private List<UserAssistantMessage> promptHistory(UUID conversationId, UUID userId) {
        List<UserAssistantMessage> messages = recentMessages(conversationId, userId, PROMPT_HISTORY_LIMIT);
        Collections.reverse(messages);
        return messages.stream().limit(PROMPT_HISTORY_LIMIT).toList();
    }

    private List<UserAssistantMessage> recentMessages(UUID conversationId, UUID userId, int limit) {
        return messageRepository.findByConversationIdAndUserIdOrderByCreatedAtDesc(
                conversationId,
                userId,
                PageRequest.of(0, limit)
        );
    }

    private AssistantRoute route(
            String userMessage,
            UserSettingsResult settings,
            UserAssistantPageContext pageContext
    ) {
        try {
            String response = openRouterChatClient.completeJson(
                    openRouterProperties.models().chatModel(),
                    routeSystemPrompt(),
                    routeUserPrompt(userMessage, settings, pageContext),
                    "assistant_route",
                    routeSchema()
            );
            AssistantRouteResponse route = parseRouteResponse(response);
            return AssistantRoute.from(route, userMessage);
        } catch (OpenRouterException | JacksonException exception) {
            log.info("Failed to classify assistant message; using local fallback ({})",
                    exception.getClass().getSimpleName());
            return AssistantRoute.fallback(userMessage);
        }
    }

    private AssistantRouteResponse parseRouteResponse(String response) throws JacksonException {
        try {
            return objectMapper.readValue(
                    OpenRouterJsonExtractor.objectCandidate(response),
                    AssistantRouteResponse.class);
        } catch (JacksonException exception) {
            Map<String, String> values = OpenRouterJsonExtractor.looseKeyValues(
                    response,
                    List.of("action", "searchQuery", "clarifyingQuestion")
            );
            if (!values.isEmpty()) {
                return new AssistantRouteResponse(
                        values.get("action"),
                        values.get("searchQuery"),
                        values.get("clarifyingQuestion")
                );
            }
            throw exception;
        }
    }

    private UserAssistantToolContext toolContext(
            UpsertUserCommand upsertCommand,
            SendUserAssistantMessageCommand command,
            AssistantRoute route
    ) {
        if (!shouldLoadSavedProducts(command.message(), command.pageContext(), route)) {
            return UserAssistantToolContext.empty();
        }
        try {
            return new UserAssistantToolContext(userSavedProductService.list(
                    upsertCommand,
                    new ListSavedProductsQuery(command.userId())
            ));
        } catch (RuntimeException exception) {
            log.warn("Failed to load assistant saved-products tool context", exception);
            return UserAssistantToolContext.empty();
        }
    }

    private String answer(
            AssistantRoute route,
            String userMessage,
            UserSettingsResult settings,
            UserAssistantPageContext pageContext,
            UserAssistantToolContext toolContext,
            List<UserAssistantMessage> history,
            List<UserProductSearchProductResult> products,
            String searchError,
            Consumer<UserAssistantStreamEvent> eventConsumer
    ) {
        if (route.isClarify()) {
            String text = route.clarifyingQuestion();
            emitText(text, eventConsumer);
            return text;
        }

        if (route.isSearch() && products.isEmpty()) {
            String text = searchError == null
                    ? "I searched for that, but I do not have matching products to recommend yet. Try broadening the request or changing the merchant scope."
                    : "I could not run that product search right now. Try again in a moment.";
            emitText(text, eventConsumer);
            return text;
        }

        StringBuilder streamed = new StringBuilder();
        try {
            openRouterChatClient.streamText(
                    openRouterProperties.models().chatModel(),
                    chatMessages(route, userMessage, settings, pageContext, toolContext, history, products),
                    chunk -> {
                        streamed.append(chunk);
                        eventConsumer.accept(UserAssistantStreamEvent.delta(chunk));
                    }
            );
        } catch (OpenRouterException exception) {
            log.warn("Failed to stream assistant answer; using local fallback", exception);
            String fallback = fallbackAnswer(route, settings, pageContext, toolContext, products);
            emitText(fallback, eventConsumer);
            return fallback;
        }

        String answer = streamed.toString().trim();
        if (answer.isBlank()) {
            String fallback = fallbackAnswer(route, settings, pageContext, toolContext, products);
            emitText(fallback, eventConsumer);
            return fallback;
        }
        return answer;
    }

    private List<OpenRouterChatMessage> chatMessages(
            AssistantRoute route,
            String userMessage,
            UserSettingsResult settings,
            UserAssistantPageContext pageContext,
            UserAssistantToolContext toolContext,
            List<UserAssistantMessage> history,
            List<UserProductSearchProductResult> products
    ) {
        List<OpenRouterChatMessage> messages = new ArrayList<>();
        messages.add(new OpenRouterChatMessage("system", chatSystemPrompt(
                route,
                settings,
                pageContext,
                toolContext,
                products)));
        history.forEach(message -> messages.add(new OpenRouterChatMessage(
                message.getRole() == UserAssistantMessageRole.USER ? "user" : "assistant",
                message.getContent()
        )));
        if (history.isEmpty() || !history.getLast().getContent().equals(userMessage)) {
            messages.add(new OpenRouterChatMessage("user", userMessage));
        }
        return messages;
    }

    private String chatSystemPrompt(
            AssistantRoute route,
            UserSettingsResult settings,
            UserAssistantPageContext pageContext,
            UserAssistantToolContext toolContext,
            List<UserProductSearchProductResult> products
    ) {
        return """
                You are Ask Meant, a concise shopping and account assistant inside the Meant app.
                Use server-loaded user data, profile preferences, and the provided app context. Treat backend profile/settings and SERVER USER DATA as authoritative.
                Treat page context as a snapshot of what the user currently sees; do not use it for authorization or irreversible actions.
                If SERVER USER DATA contains saved products, use those products for saved-item questions. Do not say saved-item details are unavailable when saved products are listed there.
                If order, cart, account, saved item, or preference data is not present in SERVER USER DATA or page context, say that you do not have that data yet.
                For shopping answers, only recommend products listed in PRODUCT SEARCH RESULTS or visible products in PAGE CONTEXT. Do not invent product names, prices, merchants, or availability.
                Do not claim that you bought, saved, changed, canceled, returned, or checked out anything.
                Do not write fake app actions or bracketed pseudo-links such as [Open item in the Meant app]. If a real app action has not already happened, say what the user can do with the visible product cards.
                Keep the answer under 120 words, direct, and useful.

                MODE:
                %s

                USER PROFILE:
                %s

                PAGE CONTEXT:
                %s

                SERVER USER DATA:
                %s

                PRODUCT SEARCH RESULTS:
                %s
                """.formatted(
                route.action(),
                profilePrompt(settings),
                pageContextPrompt(pageContext),
                toolContextPrompt(toolContext),
                productsPrompt(products)
        );
    }

    private String routeSystemPrompt() {
        return """
                Classify a Meant floating assistant message.
                Return search_products when the user wants to find, browse, compare alternatives, or get recommendations for products.
                Return clarify only when a shopping request is too vague to search because it lacks the product type, recipient, occasion, or usable constraint.
                Return answer for account, order, cart, saved-item, preference, navigation, explanation, or general follow-up questions.
                If returning search_products, write a concise merchant-search query in searchQuery.
                If returning clarify, write one short clarifyingQuestion.
                """;
    }

    private String routeUserPrompt(
            String userMessage,
            UserSettingsResult settings,
            UserAssistantPageContext pageContext
    ) {
        return """
                User message:
                %s

                User profile:
                %s

                Current app context:
                %s
                """.formatted(userMessage, profilePrompt(settings), pageContextPrompt(pageContext));
    }

    private OpenRouterJsonSchemaDefinition routeSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("action", "searchQuery", "clarifyingQuestion"),
                Map.of(
                        "action", OpenRouterJsonSchemaDefinition.stringEnum(List.of(
                                ACTION_ANSWER,
                                ACTION_SEARCH,
                                ACTION_CLARIFY
                        )),
                        "searchQuery", OpenRouterJsonSchemaDefinition.string(),
                        "clarifyingQuestion", OpenRouterJsonSchemaDefinition.string()
                )
        );
    }

    private String profilePrompt(UserSettingsResult settings) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Budget: ").append(settings.budget() == null ? "unknown" : "$" + settings.budget()).append('\n');
        if (settings.location() == null) {
            prompt.append("Location: unknown\n");
        } else {
            prompt.append("Location: ")
                    .append(settings.location().city())
                    .append(", ")
                    .append(settings.location().country())
                    .append('\n');
        }
        prompt.append("Active preferences:\n");
        if (settings.filters().isEmpty()) {
            prompt.append("- none\n");
        } else {
            settings.filters().forEach(filter -> prompt.append("- ")
                    .append(filter.label())
                    .append(" (")
                    .append(filter.polarity())
                    .append(", ")
                    .append(filter.category())
                    .append("): ")
                    .append(filter.description())
                    .append('\n'));
        }
        return prompt.toString();
    }

    private String pageContextPrompt(UserAssistantPageContext context) {
        if (context == null) {
            return "No page context provided.";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("View: ").append(value(context.view())).append('\n');
        prompt.append("Context label: ").append(value(context.contextLabel())).append('\n');
        prompt.append("Current search: ").append(value(context.currentSearchQuery())).append('\n');
        prompt.append("Selected merchant: ").append(value(context.selectedMerchantName())).append('\n');
        prompt.append("Saved products: ").append(context.savedProductCount() == null ? "unknown" : context.savedProductCount()).append('\n');
        prompt.append("Cart items: ").append(context.cartItemCount() == null ? "unknown" : context.cartItemCount()).append('\n');

        prompt.append("Visible products:\n");
        if (context.visibleProducts().isEmpty()) {
            prompt.append("- none\n");
        } else {
            context.visibleProducts().stream().limit(8).forEach(product -> prompt.append("- ")
                    .append(product.name())
                    .append(" by ")
                    .append(value(product.brand()))
                    .append("; category ")
                    .append(value(product.category()))
                    .append("; match ")
                    .append(product.match() == null ? "unknown" : product.match() + "%")
                    .append("; price ")
                    .append(product.priceFrom() == null ? "unknown" : "$" + product.priceFrom())
                    .append("; note ")
                    .append(value(product.note()))
                    .append('\n'));
        }

        prompt.append("Cart summary:\n");
        if (context.cartItems().isEmpty()) {
            prompt.append("- none\n");
        } else {
            context.cartItems().stream().limit(8).forEach(item -> prompt.append("- ")
                    .append(item.quantity() == null ? "?" : item.quantity())
                    .append(" x ")
                    .append(value(item.name()))
                    .append(" from ")
                    .append(value(item.merchant()))
                    .append("; price ")
                    .append(item.price() == null ? "unknown" : "$" + item.price())
                    .append('\n'));
        }

        prompt.append("Orders visible in app:\n");
        if (context.orders().isEmpty()) {
            prompt.append("- none\n");
        } else {
            context.orders().stream().limit(5).forEach(order -> prompt.append("- ")
                    .append(value(order.id()))
                    .append("; date ")
                    .append(value(order.date()))
                    .append("; status ")
                    .append(value(order.status()))
                    .append("; note ")
                    .append(value(order.statusNote()))
                    .append("; items ")
                    .append(order.itemCount() == null ? "unknown" : order.itemCount())
                    .append('\n'));
        }
        return prompt.toString();
    }

    private String toolContextPrompt(UserAssistantToolContext context) {
        if (context == null || !context.hasSavedProducts()) {
            return "No server-loaded user data for this turn.";
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("Saved products loaded from the user's account:\n");
        context.savedProducts().stream()
                .limit(12)
                .forEach(product -> prompt.append("- ")
                        .append(product.name())
                        .append(" by ")
                        .append(value(product.brand()))
                        .append("; category ")
                        .append(value(product.category()))
                        .append("; match ")
                        .append(product.match())
                        .append("%; price ")
                        .append(savedProductPrice(product))
                        .append("; merchants ")
                        .append(product.merchants())
                        .append("; note ")
                        .append(value(product.note()))
                        .append("; satisfies ")
                        .append(product.satisfies())
                        .append("; misses ")
                        .append(product.misses())
                        .append("; review ")
                        .append(product.review() == null ? "unknown" : product.review().score())
                        .append('\n'));
        return prompt.toString();
    }

    private String productsPrompt(List<UserProductSearchProductResult> products) {
        if (products.isEmpty()) {
            return "No product search results provided.";
        }
        StringBuilder prompt = new StringBuilder();
        for (int index = 0; index < products.size(); index++) {
            UserProductSearchProductResult product = products.get(index);
            prompt.append(index + 1)
                    .append(". ")
                    .append(product.title())
                    .append("; merchant ")
                    .append(value(product.merchantName() == null ? product.merchantDomain() : product.merchantName()))
                    .append("; match ")
                    .append(product.matchScore())
                    .append("%; price ")
                    .append(productPrice(product))
                    .append("; why ")
                    .append(value(product.whyMeantForYou()))
                    .append("; matched filters ")
                    .append(product.matchedFilterIds())
                    .append("; missed filters ")
                    .append(product.missedFilterIds())
                    .append('\n');
        }
        return prompt.toString();
    }

    private String productPrice(UserProductSearchProductResult product) {
        if (product.selectedVariantPriceAmount() != null && !product.selectedVariantPriceAmount().isBlank()) {
            return product.selectedVariantPriceAmount() + " " + value(product.selectedVariantPriceCurrency());
        }
        if (product.detailPriceMin() != null && !product.detailPriceMin().isBlank()) {
            return product.detailPriceMin() + " " + value(product.detailPriceCurrency());
        }
        if (product.priceMinAmount() == null) {
            return "unknown";
        }
        double amount = product.priceMinAmount() > 999 ? product.priceMinAmount() / 100.0 : product.priceMinAmount();
        return "$%.2f %s".formatted(amount, value(product.priceCurrency()));
    }

    private String savedProductPrice(UserSavedProductResult product) {
        return "$%.2f".formatted(product.priceFrom());
    }

    private String fallbackAnswer(
            AssistantRoute route,
            UserSettingsResult settings,
            UserAssistantPageContext pageContext,
            UserAssistantToolContext toolContext,
            List<UserProductSearchProductResult> products
    ) {
        if (route.isSearch() && !products.isEmpty()) {
            String productTitles = String.join(", ", products.stream()
                    .map(UserProductSearchProductResult::title)
                    .filter(title -> title != null && !title.isBlank())
                    .toList());
            return "I found " + products.size() + " strong option"
                    + (products.size() == 1 ? "" : "s")
                    + ": "
                    + (productTitles.isBlank() ? "the top results" : productTitles)
                    + ". The first result is the safest place to start for your current preferences.";
        }
        if (toolContext != null && toolContext.hasSavedProducts()) {
            return savedProductsFallbackAnswer(toolContext.savedProducts());
        }
        if (pageContext != null && !pageContext.orders().isEmpty()) {
            UserAssistantPageContext.Order order = pageContext.orders().getFirst();
            return "Your latest visible order is " + order.id() + ", marked " + order.status()
                    + ". " + value(order.statusNote());
        }
        if (pageContext != null && !pageContext.visibleProducts().isEmpty()) {
            UserAssistantPageContext.Product product = pageContext.visibleProducts().stream()
                    .max((left, right) -> Integer.compare(score(left.match()), score(right.match())))
                    .orElse(pageContext.visibleProducts().getFirst());
            return "From the products visible here, I would start with " + product.name()
                    + ". It has the strongest current match"
                    + (product.match() == null ? "" : " at " + product.match() + "%")
                    + (product.note() == null || product.note().isBlank() ? "." : ": " + product.note());
        }
        List<String> preferences = settings.filters().stream()
                .map(ShoppingFilterResult::label)
                .limit(4)
                .toList();
        return "I can help with product searches, saved items, cart context, and preferences. Your active preferences include "
                + (preferences.isEmpty() ? "none yet" : String.join(", ", preferences))
                + ".";
    }

    private String savedProductsFallbackAnswer(List<UserSavedProductResult> savedProducts) {
        UserSavedProductResult product = savedProducts.stream()
                .max((left, right) -> Integer.compare(left.match(), right.match()))
                .orElse(savedProducts.getFirst());
        StringBuilder answer = new StringBuilder();
        answer.append("From your saved products, I would start with ")
                .append(product.name())
                .append(". It has the strongest saved match at ")
                .append(product.match())
                .append("%");
        if (product.priceFrom() > 0) {
            answer.append(" and starts around ")
                    .append(savedProductPrice(product));
        }
        if (product.note() != null && !product.note().isBlank()) {
            answer.append(". ")
                    .append(product.note());
        } else if (!product.satisfies().isEmpty()) {
            answer.append(". It fits ")
                    .append(String.join(", ", product.satisfies().stream().limit(3).toList()))
                    .append(".");
        } else {
            answer.append(".");
        }
        return answer.toString();
    }

    private void emitText(String text, Consumer<UserAssistantStreamEvent> eventConsumer) {
        for (String chunk : text.split("(?<=\\s)")) {
            if (!chunk.isBlank()) {
                eventConsumer.accept(UserAssistantStreamEvent.delta(chunk));
            }
        }
    }

    private String title(String message) {
        String normalized = message.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 77) + "...";
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private int score(Integer value) {
        return value == null ? -1 : value;
    }

    private boolean shouldLoadSavedProducts(
            String userMessage,
            UserAssistantPageContext pageContext,
            AssistantRoute route
    ) {
        String normalized = normalize(userMessage);
        return normalized.contains("saved")
                || normalized.contains("save list")
                || normalized.contains("wishlist")
                || normalized.contains("products i have")
                || normalized.contains("products that i have")
                || normalized.contains("items i have")
                || normalized.contains("my products")
                || "saved".equalsIgnoreCase(pageContext == null ? null : pageContext.view())
                || (route != null && route.isSearch() && normalized.contains("from products"));
    }

    private boolean shouldAnswerFromSavedProducts(String userMessage, UserAssistantPageContext pageContext) {
        String normalized = normalize(userMessage);
        boolean savedReference = shouldLoadSavedProducts(userMessage, pageContext, null);
        boolean comparisonQuestion = normalized.matches(".*\\b(best|better|which|compare|pick|choose|recommend|worth|start)\\b.*")
                || normalized.contains("what is the best");
        boolean explicitDiscovery = normalized.matches(".*\\b(find|search|show|browse|buy|alternative|alternatives|similar)\\b.*");
        return savedReference && comparisonQuestion && !explicitDiscovery;
    }

    private String normalize(String value) {
        return value == null ? "" : value
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .toLowerCase(Locale.ROOT);
    }

    private String pageContextJson(UserAssistantPageContext pageContext) {
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

    private record AssistantRouteResponse(
            String action,
            String searchQuery,
            String clarifyingQuestion
    ) {
    }

    private record AssistantRoute(
            String action,
            String searchQuery,
            String clarifyingQuestion
    ) {

        private static AssistantRoute from(AssistantRouteResponse response, String fallbackQuery) {
            String action = normalizeAction(response == null ? null : response.action());
            String searchQuery = textOrFallback(response == null ? null : response.searchQuery(), fallbackQuery);
            String clarifyingQuestion = textOrFallback(
                    response == null ? null : response.clarifyingQuestion(),
                    "What kind of product or occasion should I focus on?"
            );
            return new AssistantRoute(action, searchQuery, clarifyingQuestion);
        }

        private static AssistantRoute fallback(String userMessage) {
            String normalized = userMessage.toLowerCase(Locale.ROOT);
            if (asksAboutExistingContext(normalized)) {
                return new AssistantRoute(ACTION_ANSWER, userMessage, "");
            }
            boolean likelySearch = normalized.matches(".*\\b(find|search|recommend|show|buy|gift|under|cheaper|alternative|best)\\b.*");
            return new AssistantRoute(likelySearch ? ACTION_SEARCH : ACTION_ANSWER, userMessage, "");
        }

        private boolean isSearch() {
            return ACTION_SEARCH.equals(action);
        }

        private boolean isClarify() {
            return ACTION_CLARIFY.equals(action);
        }

        private AssistantRoute asAnswer() {
            return new AssistantRoute(ACTION_ANSWER, searchQuery, clarifyingQuestion);
        }

        private static String normalizeAction(String action) {
            if (action == null || action.isBlank()) {
                return ACTION_ANSWER;
            }
            String normalized = action.trim()
                    .toLowerCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_');
            if (ACTION_SEARCH.equals(normalized)
                    || normalized.contains("search")) {
                return ACTION_SEARCH;
            }
            if (ACTION_CLARIFY.equals(normalized)
                    || normalized.contains("clarif")) {
                return ACTION_CLARIFY;
            }
            if (ACTION_ANSWER.equals(normalized)
                    || normalized.contains("answer")) {
                return ACTION_ANSWER;
            }
            return ACTION_ANSWER;
        }

        private static boolean asksAboutExistingContext(String normalized) {
            return normalized.matches(".*\\b(saved|cart|order|orders|account|profile|preference|preferences|visible|shown|current|already)\\b.*")
                    || normalized.contains("products i have")
                    || normalized.contains("products that i have")
                    || normalized.contains("items i have")
                    || normalized.contains("my products");
        }

        private static String textOrFallback(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }
    }
}
