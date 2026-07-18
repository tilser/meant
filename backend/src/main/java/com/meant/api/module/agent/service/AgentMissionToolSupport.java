package com.meant.api.module.agent.service;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.ShoppingMissionStatus;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.ShoppingMission;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.ShoppingMissionRepository;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentMissionDetails;
import com.meant.api.module.agent.service.dto.AgentMissionToolArguments;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
class AgentMissionToolSupport {

    private static final int MAX_ARGUMENT_BYTES = 64_000;
    private static final TypeReference<List<AgentMissionDetails.Assumption>> ASSUMPTIONS = new TypeReference<>() { };
    private static final TypeReference<List<AgentMissionDetails.Requirement>> REQUIREMENTS = new TypeReference<>() { };
    private static final TypeReference<List<AgentMissionDetails.Alternative>> ALTERNATIVES = new TypeReference<>() { };
    private static final TypeReference<List<AgentMissionDetails.Coverage>> COVERAGE = new TypeReference<>() { };
    private static final TypeReference<List<UUID>> UUIDS = new TypeReference<>() { };

    private final AgentConversationRepository conversationRepository;
    private final ShoppingMissionRepository missionRepository;
    private final AgentProductReadReferenceService referenceService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final AgentJsonSupport jsonSupport;

    <T> T arguments(String json, Class<T> type) {
        if (json == null || json.isBlank() || json.length() > MAX_ARGUMENT_BYTES) {
            throw invalid("Tool arguments must be a bounded JSON object.");
        }
        try {
            T value = objectMapper.readValue(json, type);
            if (value == null) {
                throw invalid("Tool arguments must be a JSON object.");
            }
            Set<ConstraintViolation<T>> violations = validator.validate(value);
            if (!violations.isEmpty()) {
                String field = violations.stream()
                        .map(violation -> violation.getPropertyPath().toString())
                        .sorted()
                        .findFirst()
                        .orElse("arguments");
                throw invalid("Invalid tool argument: " + field + ".");
            }
            return value;
        } catch (AgentException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw invalid("Tool arguments are not valid JSON.");
        }
    }

    @Transactional
    AgentMissionDetails create(AgentToolExecutionContext context, AgentMissionToolArguments.Create arguments) {
        AgentConversation conversation = ownedConversationForUpdate(context);
        if (conversation.getActiveMissionId() != null) {
            missionRepository.findByIdAndUserId(conversation.getActiveMissionId(), context.userId())
                    .filter(mission -> !terminal(mission.getStatus()))
                    .ifPresent(mission -> {
                        throw AgentException.conflict("This conversation already has an active shopping mission.");
                    });
        }
        List<AgentMissionDetails.Assumption> assumptions = list(arguments.assumptions());
        List<AgentMissionDetails.Requirement> requirements = list(arguments.requirements());
        AgentMissionDetails.Constraints constraints = constraints(arguments.constraints());
        List<AgentMissionDetails.Alternative> alternatives = list(arguments.alternatives());
        validateAssumptions(assumptions);
        validateStructure(context, requirements, alternatives);
        List<AgentMissionDetails.Coverage> coverage = evaluate(
                context,
                requirements,
                selectedAlternatives(alternatives)
        );
        Instant now = Instant.now();
        ShoppingMission mission = ShoppingMission.builder()
                .conversationId(context.conversationId())
                .userId(context.userId())
                .goal(arguments.goal().trim())
                .status(readiness(requirements, coverage))
                .assumptionsJson(json(assumptions))
                .requirementsJson(json(requirements))
                .constraintsJson(json(constraints))
                .alternativesJson(json(alternatives))
                .coverageJson(json(coverage))
                .cartReferencesJson(json(List.of()))
                .checkoutReferencesJson(json(List.of()))
                .createdAt(now)
                .updatedAt(now)
                .build();
        missionRepository.save(mission);
        conversation.activateMission(mission.getId(), now);
        conversationRepository.save(conversation);
        return details(mission);
    }

    @Transactional
    AgentMissionDetails update(AgentToolExecutionContext context, AgentMissionToolArguments.Update arguments) {
        AgentConversation conversation = ownedConversationForUpdate(context);
        ShoppingMission mission = ownedMissionForUpdate(context, arguments.missionId());
        List<AgentMissionDetails.Assumption> assumptions = arguments.assumptions() == null
                ? assumptions(mission) : List.copyOf(arguments.assumptions());
        List<AgentMissionDetails.Requirement> requirements = arguments.requirements() == null
                ? requirements(mission) : List.copyOf(arguments.requirements());
        AgentMissionDetails.Constraints constraints = arguments.constraints() == null
                ? constraints(mission) : constraints(arguments.constraints());
        List<AgentMissionDetails.Alternative> alternatives = arguments.alternatives() == null
                ? alternatives(mission) : List.copyOf(arguments.alternatives());
        List<UUID> cartIds = cartIds(mission);
        List<UUID> checkoutIds = checkoutIds(mission);
        validateAssumptions(assumptions);
        validateStructure(context, requirements, alternatives);
        List<AgentMissionDetails.CoverageSelection> retained = arguments.requirements() == null
                && arguments.alternatives() == null
                ? coverage(mission).stream().flatMap(value -> value.selections().stream()).toList()
                : selectedAlternatives(alternatives);
        List<AgentMissionDetails.Coverage> coverage = evaluate(context, requirements, retained);
        ShoppingMissionStatus nextStatus = status(
                mission.getStatus(), arguments.status(), requirements, coverage, checkoutIds);
        String goal = arguments.goal() == null ? mission.getGoal() : requiredText(arguments.goal(), "goal");
        Instant now = Instant.now();
        mission.update(
                goal,
                nextStatus,
                json(assumptions),
                json(requirements),
                json(constraints),
                json(alternatives),
                json(coverage),
                json(cartIds),
                json(checkoutIds),
                now
        );
        ShoppingMission saved = missionRepository.save(mission);
        if (terminal(nextStatus) && mission.getId().equals(conversation.getActiveMissionId())) {
            conversation.activateMission(null, now);
            conversationRepository.save(conversation);
        }
        return details(saved);
    }

    @Transactional
    AgentMissionDetails evaluateCoverage(
            AgentToolExecutionContext context,
            AgentMissionToolArguments.EvaluateCoverage arguments
    ) {
        ownedConversationForUpdate(context);
        ShoppingMission mission = ownedMissionForUpdate(context, arguments.missionId());
        List<AgentMissionDetails.Requirement> requirements = requirements(mission);
        List<AgentMissionDetails.Alternative> alternatives = alternatives(mission);
        List<AgentMissionDetails.CoverageSelection> selections = arguments.selections() == null
                ? selectedAlternatives(alternatives)
                : List.copyOf(arguments.selections());
        validateStructure(context, requirements, alternatives);
        List<AgentMissionDetails.Coverage> coverage = evaluate(context, requirements, selections);
        ShoppingMissionStatus nextStatus = status(
                mission.getStatus(), null, requirements, coverage, checkoutIds(mission));
        mission.update(
                mission.getGoal(),
                nextStatus,
                mission.getAssumptionsJson(),
                mission.getRequirementsJson(),
                mission.getConstraintsJson(),
                mission.getAlternativesJson(),
                json(coverage),
                mission.getCartReferencesJson(),
                mission.getCheckoutReferencesJson(),
                Instant.now()
        );
        return details(missionRepository.save(mission));
    }

    @Transactional
    void attachCartReferences(AgentToolExecutionContext context, List<UUID> cartIds) {
        if (cartIds == null || cartIds.isEmpty()) {
            return;
        }
        AgentConversation conversation = ownedConversationForUpdate(context);
        if (conversation.getActiveMissionId() == null) {
            return;
        }
        ShoppingMission mission = ownedMissionForUpdate(context, conversation.getActiveMissionId());
        if (terminal(mission.getStatus())) {
            return;
        }
        List<UUID> merged = merge(cartIds(mission), cartIds);
        mission.update(
                mission.getGoal(), mission.getStatus(), mission.getAssumptionsJson(), mission.getRequirementsJson(),
                mission.getConstraintsJson(), mission.getAlternativesJson(), mission.getCoverageJson(), json(merged),
                mission.getCheckoutReferencesJson(), Instant.now());
        missionRepository.save(mission);
    }

    @Transactional
    void attachCheckoutReferences(AgentToolExecutionContext context, List<UUID> checkoutAttemptIds) {
        if (checkoutAttemptIds == null || checkoutAttemptIds.isEmpty()) {
            return;
        }
        AgentConversation conversation = ownedConversationForUpdate(context);
        if (conversation.getActiveMissionId() == null) {
            return;
        }
        ShoppingMission mission = ownedMissionForUpdate(context, conversation.getActiveMissionId());
        if (terminal(mission.getStatus())) {
            return;
        }
        List<UUID> merged = merge(checkoutIds(mission), checkoutAttemptIds);
        mission.update(
                mission.getGoal(), ShoppingMissionStatus.CHECKOUT_PREPARED,
                mission.getAssumptionsJson(), mission.getRequirementsJson(),
                mission.getConstraintsJson(), mission.getAlternativesJson(), mission.getCoverageJson(),
                mission.getCartReferencesJson(), json(merged), Instant.now());
        missionRepository.save(mission);
    }

    String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw internal("Could not serialize agent tool data.", exception);
        }
    }

    String toolJson(Object value) {
        return jsonSupport.write(value);
    }

    AgentArtifact artifact(AgentMissionDetails details, String payloadJson) {
        return new AgentArtifact(
                AgentArtifactType.MISSION,
                1,
                "mission:" + details.missionId(),
                details.goal(),
                null,
                null,
                null,
                null,
                null,
                null,
                payloadJson
        );
    }

    private AgentConversation ownedConversationForUpdate(AgentToolExecutionContext context) {
        requireContext(context);
        return conversationRepository.findOwnedForUpdate(context.conversationId(), context.userId())
                .orElseThrow(AgentException::notFound);
    }

    private ShoppingMission ownedMissionForUpdate(AgentToolExecutionContext context, UUID missionId) {
        ShoppingMission mission = missionRepository.findOwnedForUpdate(missionId, context.userId())
                .orElseThrow(AgentException::notFound);
        if (!mission.getConversationId().equals(context.conversationId())) {
            throw AgentException.notFound();
        }
        return mission;
    }

    private void requireContext(AgentToolExecutionContext context) {
        if (context == null || context.userId() == null || context.conversationId() == null
                || context.triggeringMessageId() == null) {
            throw AgentException.notFound();
        }
    }

    private void validateStructure(
            AgentToolExecutionContext context,
            List<AgentMissionDetails.Requirement> requirements,
            List<AgentMissionDetails.Alternative> alternatives
    ) {
        if (requirements.stream().anyMatch(java.util.Objects::isNull)
                || alternatives.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("Mission requirements and alternatives must not contain null entries.");
        }
        Set<String> ids = new LinkedHashSet<>();
        Set<String> labels = new LinkedHashSet<>();
        for (AgentMissionDetails.Requirement requirement : requirements) {
            String id = normalized(requirement.id());
            String label = normalized(requirement.label());
            if (!ids.add(id) || !labels.add(label)) {
                throw invalid("Mission requirements must have unique IDs and labels.");
            }
        }
        Set<String> alternativeKeys = new LinkedHashSet<>();
        for (AgentMissionDetails.Alternative alternative : alternatives) {
            String requirementId = normalized(alternative.requirementId());
            if (!ids.contains(requirementId)) {
                throw invalid("A mission alternative references an unknown requirement.");
            }
            requireReference(context, alternative.canonicalProductKey(), alternative.offerKey(), null);
            String key = requirementId + "\n" + referenceKey(
                    alternative.canonicalProductKey(), alternative.offerKey(), null);
            if (!alternativeKeys.add(key)) {
                throw invalid("Mission alternatives must not contain duplicate products.");
            }
        }
    }

    private void validateAssumptions(List<AgentMissionDetails.Assumption> assumptions) {
        if (assumptions.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("Mission assumptions must not contain null entries.");
        }
    }

    private List<AgentMissionDetails.Coverage> evaluate(
            AgentToolExecutionContext context,
            List<AgentMissionDetails.Requirement> requirements,
            List<AgentMissionDetails.CoverageSelection> rawSelections
    ) {
        Map<String, AgentMissionDetails.Requirement> byId = new LinkedHashMap<>();
        requirements.forEach(requirement -> byId.put(normalized(requirement.id()), requirement));
        Map<String, Map<String, AgentMissionDetails.CoverageSelection>> selected = new LinkedHashMap<>();
        for (AgentMissionDetails.CoverageSelection selection : list(rawSelections)) {
            if (selection == null) {
                throw invalid("Mission coverage must not contain null entries.");
            }
            String requirementId = normalized(selection.requirementId());
            if (!byId.containsKey(requirementId)) {
                throw invalid("Mission coverage references an unknown requirement.");
            }
            requireReference(
                    context,
                    selection.canonicalProductKey(),
                    selection.offerKey(),
                    selection.inventoryItemId()
            );
            selected.computeIfAbsent(requirementId, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(referenceKey(
                            selection.canonicalProductKey(), selection.offerKey(), selection.inventoryItemId()),
                            selection);
        }
        List<AgentMissionDetails.Coverage> result = new ArrayList<>();
        for (AgentMissionDetails.Requirement requirement : requirements) {
            int required = requirement.requiredQuantity() == null ? 1 : requirement.requiredQuantity();
            List<AgentMissionDetails.CoverageSelection> selections = new ArrayList<>(selected
                    .getOrDefault(normalized(requirement.id()), Map.of()).values());
            int covered = selections.stream().mapToInt(selection -> selection.quantity() == null
                    ? 1 : selection.quantity()).sum();
            AgentMissionDetails.CoverageState state = covered >= required
                    ? AgentMissionDetails.CoverageState.COVERED
                    : covered > 0
                            ? AgentMissionDetails.CoverageState.PARTIAL
                            : requirement.optional()
                                    ? AgentMissionDetails.CoverageState.OPTIONAL
                                    : AgentMissionDetails.CoverageState.MISSING;
            result.add(new AgentMissionDetails.Coverage(
                    requirement.id(), state, required, covered, selections));
        }
        return List.copyOf(result);
    }

    private List<AgentMissionDetails.CoverageSelection> selectedAlternatives(
            List<AgentMissionDetails.Alternative> alternatives
    ) {
        return alternatives.stream()
                .filter(AgentMissionDetails.Alternative::selected)
                .map(alternative -> new AgentMissionDetails.CoverageSelection(
                        alternative.requirementId(), alternative.canonicalProductKey(), alternative.offerKey(), null, 1))
                .toList();
    }

    private ShoppingMissionStatus readiness(
            List<AgentMissionDetails.Requirement> requirements,
            List<AgentMissionDetails.Coverage> coverage
    ) {
        boolean hasRequired = requirements.stream().anyMatch(requirement -> !requirement.optional());
        boolean covered = coverage.stream()
                .filter(value -> value.state() != AgentMissionDetails.CoverageState.OPTIONAL)
                .allMatch(value -> value.state() == AgentMissionDetails.CoverageState.COVERED);
        return hasRequired && covered ? ShoppingMissionStatus.READY : ShoppingMissionStatus.ACTIVE;
    }

    private ShoppingMissionStatus status(
            ShoppingMissionStatus current,
            ShoppingMissionStatus requested,
            List<AgentMissionDetails.Requirement> requirements,
            List<AgentMissionDetails.Coverage> coverage,
            List<UUID> checkoutIds
    ) {
        if (terminal(current)) {
            if (requested != null && requested != current) {
                throw AgentException.conflict("A terminal shopping mission cannot be reopened.");
            }
            return current;
        }
        if (requested == ShoppingMissionStatus.CANCELLED || requested == ShoppingMissionStatus.COMPLETED) {
            return requested;
        }
        if (requested == ShoppingMissionStatus.CHECKOUT_PREPARED) {
            if (checkoutIds.isEmpty()) {
                throw invalid("A checkout reference is required before checkout can be marked prepared.");
            }
            return requested;
        }
        if (current == ShoppingMissionStatus.CHECKOUT_PREPARED && requested == null) {
            return current;
        }
        return readiness(requirements, coverage);
    }

    private boolean terminal(ShoppingMissionStatus status) {
        return status == ShoppingMissionStatus.COMPLETED || status == ShoppingMissionStatus.CANCELLED;
    }

    private void requireReference(
            AgentToolExecutionContext context,
            String canonicalProductKey,
            String offerKey,
            UUID inventoryItemId
    ) {
        long present = java.util.stream.Stream.of(canonicalProductKey, offerKey)
                .filter(value -> value != null && !value.isBlank()).count();
        if (inventoryItemId != null) {
            present += 1;
        }
        if (present == 0) {
            throw invalid("Mission selections require a server-issued product, offer, or inventory reference.");
        }
        if (offerKey != null && !offerKey.isBlank()
                && (canonicalProductKey == null || canonicalProductKey.isBlank())) {
            throw invalid("An offer reference must include its server-issued canonical product key.");
        }
        if (canonicalProductKey != null && !canonicalProductKey.isBlank()) {
            referenceService.requireProduct(context, canonicalProductKey.trim());
        }
        if (offerKey != null && !offerKey.isBlank()) {
            referenceService.requireOffer(context, canonicalProductKey.trim(), offerKey.trim());
        }
        if (inventoryItemId != null) {
            referenceService.requireInventoryItem(context, inventoryItemId);
        }
    }

    private String referenceKey(String canonicalProductKey, String offerKey, UUID inventoryItemId) {
        if (offerKey != null && !offerKey.isBlank()) {
            return "offer:" + offerKey.trim();
        }
        if (canonicalProductKey != null && !canonicalProductKey.isBlank()) {
            return "product:" + canonicalProductKey.trim();
        }
        return "inventory:" + inventoryItemId;
    }

    private AgentMissionDetails details(ShoppingMission mission) {
        return new AgentMissionDetails(
                mission.getId(),
                mission.getConversationId(),
                mission.getGoal(),
                mission.getStatus(),
                assumptions(mission),
                requirements(mission),
                constraints(mission),
                alternatives(mission),
                coverage(mission),
                cartIds(mission),
                checkoutIds(mission),
                mission.getCreatedAt(),
                mission.getUpdatedAt()
        );
    }

    private List<AgentMissionDetails.Assumption> assumptions(ShoppingMission mission) {
        return read(mission.getAssumptionsJson(), ASSUMPTIONS);
    }

    private List<AgentMissionDetails.Requirement> requirements(ShoppingMission mission) {
        return read(mission.getRequirementsJson(), REQUIREMENTS);
    }

    private AgentMissionDetails.Constraints constraints(ShoppingMission mission) {
        try {
            AgentMissionDetails.Constraints value = objectMapper.readValue(
                    mission.getConstraintsJson(), AgentMissionDetails.Constraints.class);
            return constraints(value);
        } catch (JacksonException exception) {
            throw internal("Stored mission constraints are invalid.", exception);
        }
    }

    private List<AgentMissionDetails.Alternative> alternatives(ShoppingMission mission) {
        return read(mission.getAlternativesJson(), ALTERNATIVES);
    }

    private List<AgentMissionDetails.Coverage> coverage(ShoppingMission mission) {
        return read(mission.getCoverageJson(), COVERAGE);
    }

    private List<UUID> cartIds(ShoppingMission mission) {
        return read(mission.getCartReferencesJson(), UUIDS);
    }

    private List<UUID> checkoutIds(ShoppingMission mission) {
        return read(mission.getCheckoutReferencesJson(), UUIDS);
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw internal("Stored mission data is invalid.", exception);
        }
    }

    private AgentMissionDetails.Constraints constraints(AgentMissionDetails.Constraints value) {
        return value == null ? new AgentMissionDetails.Constraints(null, null, List.of(), null, null) : value;
    }

    private <T> List<T> list(List<T> value) {
        return value == null ? List.of() : List.copyOf(value);
    }

    private List<UUID> merge(List<UUID> existing, List<UUID> additions) {
        LinkedHashSet<UUID> merged = new LinkedHashSet<>(existing);
        merged.addAll(additions);
        return List.copyOf(merged);
    }

    private String normalized(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid("Mission " + field + " must not be blank.");
        }
        return value.trim();
    }

    private AgentException invalid(String message) {
        return new AgentException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, message);
    }

    private AgentException internal(String message, Throwable cause) {
        return new AgentException(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR, message, cause);
    }
}
