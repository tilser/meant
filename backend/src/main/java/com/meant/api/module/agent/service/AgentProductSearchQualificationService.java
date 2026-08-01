package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.service.command.QualifyAgentProductSearchCommand;
import com.meant.api.module.agent.service.dto.AgentAppliedSearchFilter;
import com.meant.api.module.agent.service.dto.AgentProductSearchQualificationResult;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.service.UserProductSearchQualificationPersistenceService;
import com.meant.api.module.user.service.UserProductSearchQualificationPlanMapper;
import com.meant.api.module.user.service.UserProductSearchQualificationService;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import com.meant.api.module.user.service.dto.UserProductSearchConversationMessage;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/** Produces conversation-aware search filters as advice without gating catalog execution. */
@Service
@Validated
@RequiredArgsConstructor
public class AgentProductSearchQualificationService {

    private static final int CONVERSATION_MESSAGE_OVERHEAD_CHARACTERS = 32;
    private static final int MAXIMUM_CONVERSATION_MESSAGE_CHARACTERS = 4_000;

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final UserProductSearchQualificationService qualificationService;
    private final UserProductSearchQualificationPersistenceService persistenceService;
    private final UserProductSearchQualificationPlanMapper planMapper;
    private final AgentProperties agentProperties;

    public AgentProductSearchQualificationResult qualify(
            @NotNull @Valid QualifyAgentProductSearchCommand command
    ) {
        requireOwnedConversation(
                command.profile().id(),
                command.conversationId(),
                command.merchantId()
        );
        String currentTurn = command.authoritativeUserText().trim();
        var result = qualificationService.qualify(command.profile(), new QualifyUserProductSearchCommand(
                command.profile().id(),
                command.conversationId(),
                null,
                currentTurn,
                command.merchantId(),
                conversation(command.conversationId(), command.contextMessageId()),
                command.requestId(),
                null,
                command.trustedReferenceProductText()
        ));
        var snapshot = persistenceService.find(new GetUserProductSearchQualificationQuery(
                        command.profile().id(),
                        result.qualificationId()
                ))
                .orElseThrow(() -> new IllegalStateException(
                        "Persisted agent product-search qualification was not found"));
        UserProductSearchQualificationPlan plan = snapshot.plan();
        return new AgentProductSearchQualificationResult(
                result.qualificationId(),
                plan.effectiveQuery(),
                planMapper.mapAvailable(plan),
                appliedFilters(plan),
                plan.missingTargets(),
                plan.explicitAnyTargets(),
                plan.profileSuppressionTargets()
        );
    }

    private Map<String, AgentAppliedSearchFilter> appliedFilters(UserProductSearchQualificationPlan plan) {
        Map<String, AgentAppliedSearchFilter> filters = new LinkedHashMap<>();
        put(filters, "available", plan.available().state(), plan.available().provenance(),
                plan.available().value() == null ? List.of() : List.of(plan.available().value().toString()));
        put(filters, "condition", plan.condition().state(), plan.condition().provenance(),
                plan.condition().values().stream().map(Enum::name).toList());
        put(filters, "shipsTo", plan.shipsTo().state(), plan.shipsTo().provenance(),
                location(plan.shipsTo().value()));
        put(filters, "shipsFrom", plan.shipsFrom().state(), plan.shipsFrom().provenance(),
                plan.shipsFrom().values().stream().flatMap(value -> location(value).stream()).toList());
        put(filters, "price", plan.price().state(), plan.price().provenance(),
                range(plan.price().minUsdMinor(), plan.price().maxUsdMinor()));
        put(filters, "shops", plan.shops().state(), plan.shops().provenance(), plan.shops().values());
        put(filters, "categories", plan.categories().state(), plan.categories().provenance(),
                plan.categories().values());
        plan.attributes().values().forEach(attribute -> put(
                filters,
                switch (attribute.name()) {
                    case COLOR -> "color";
                    case SIZE -> "size";
                    case TARGET_GENDER -> "targetGender";
                },
                attribute.state(),
                attribute.provenance(),
                attribute.values()
        ));
        put(filters, "rating", plan.rating().state(), plan.rating().provenance(),
                range(plan.rating().min(), plan.rating().minCount()));
        put(filters, "priceTier", plan.priceTier().state(), plan.priceTier().provenance(),
                plan.priceTier().values().stream().map(Enum::name).toList());
        return Map.copyOf(filters);
    }

    private void put(
            Map<String, AgentAppliedSearchFilter> filters,
            String name,
            UserProductSearchFilterState state,
            UserProductSearchQualificationPlan.Provenance provenance,
            List<String> values
    ) {
        if (state == UserProductSearchFilterState.VALUE && values != null && !values.isEmpty()) {
            filters.put(name, new AgentAppliedSearchFilter(values, provenance.source()));
        }
    }

    private List<String> location(UserProductSearchQualificationPlan.Location location) {
        if (location == null) {
            return List.of();
        }
        return List.of(java.util.stream.Stream.of(
                        location.country(), location.region(), location.postalCode())
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(" / ")));
    }

    private List<String> range(Number minimum, Number maximum) {
        List<String> values = new ArrayList<>();
        if (minimum != null) {
            values.add("min=" + minimum);
        }
        if (maximum != null) {
            values.add("max=" + maximum);
        }
        return List.copyOf(values);
    }

    private List<UserProductSearchConversationMessage> conversation(
            UUID conversationId,
            UUID triggeringMessageId
    ) {
        if (triggeringMessageId == null) {
            return List.of();
        }
        var triggering = messageRepository.findById(triggeringMessageId)
                .filter(message -> message.getConversationId().equals(conversationId))
                .filter(message -> message.getRole() == AgentMessageRole.USER
                        || message.getRole() == AgentMessageRole.USER_ACTION)
                .orElseThrow(AgentException::notFound);
        var messages = messageRepository.findBuyerVisibleConversationMessages(
                conversationId,
                triggering.getSequenceNumber(),
                List.of(AgentMessageRole.USER, AgentMessageRole.USER_ACTION, AgentMessageRole.ASSISTANT),
                PageRequest.of(0, agentProperties.contextMessageBudget())
        );
        int remainingCharacters = Math.max(1, agentProperties.contextCharacterBudget() / 2);
        List<UserProductSearchConversationMessage> newestFirst = new ArrayList<>();
        for (var message : messages) {
            int maximumCharacters = Math.min(
                    MAXIMUM_CONVERSATION_MESSAGE_CHARACTERS,
                    remainingCharacters - CONVERSATION_MESSAGE_OVERHEAD_CHARACTERS
            );
            if (maximumCharacters <= 0) {
                break;
            }
            UserProductSearchConversationMessage projected = conversationMessage(message, maximumCharacters);
            if (projected != null) {
                newestFirst.add(projected);
                remainingCharacters = Math.max(
                        0,
                        remainingCharacters - projected.text().length()
                                - CONVERSATION_MESSAGE_OVERHEAD_CHARACTERS
                );
            }
        }
        java.util.Collections.reverse(newestFirst);
        return List.copyOf(newestFirst);
    }

    private UserProductSearchConversationMessage conversationMessage(
            com.meant.api.module.agent.entity.AgentMessage message,
            int maximumCharacters
    ) {
        if (message.getTextContent() == null || message.getTextContent().isBlank()) {
            return null;
        }
        UserProductSearchConversationMessage.Role role = message.getRole() == AgentMessageRole.ASSISTANT
                ? UserProductSearchConversationMessage.Role.ASSISTANT
                : UserProductSearchConversationMessage.Role.USER;
        String text = message.getTextContent().trim();
        if (text.length() > maximumCharacters) {
            text = maximumCharacters == 1 ? "…" : text.substring(0, maximumCharacters - 1) + "…";
        }
        return new UserProductSearchConversationMessage(role, text);
    }

    private void requireOwnedConversation(UUID userId, UUID conversationId, UUID merchantId) {
        var conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(AgentException::notFound);
        if (!Objects.equals(conversation.getMerchantId(), merchantId)) {
            throw AgentException.notFound();
        }
    }
}
