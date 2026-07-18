package com.meant.api.module.agent.service;

import static com.meant.api.module.agent.support.ScriptedAgentModelGateway.response;
import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentModelMessage;
import com.meant.api.module.agent.service.dto.AgentModelRequest;
import com.meant.api.module.agent.service.dto.AgentModelResponse;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentModelToolDefinition;
import com.meant.api.module.agent.service.dto.AgentModelToolResult;
import com.meant.api.module.agent.service.dto.AgentModelUsage;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.support.ScriptedAgentModelGateway;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Versioned, network-free product evaluations. The invariants intentionally cover tool selection and verified
 * round-trips rather than brittle assistant prose.
 */
class AgentNorthStarScenarioEvaluationTest {

    private static final String FIXTURE = "/agent/scenarios/north-star-v1.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> northStarScenariosUseGroundedToolsAndNeverExposePurchaseCompletion() throws IOException {
        return scenarios().stream().map(scenario -> DynamicTest.dynamicTest(
                scenario.id(),
                () -> evaluate(scenario)
        ));
    }

    private void evaluate(Scenario scenario) {
        ScriptedAgentModelGateway model = new ScriptedAgentModelGateway(scenario.steps().stream()
                .map(step -> response(
                        new AgentModelResponse(
                                step.text() == null ? "" : step.text(),
                                step.toolCalls(),
                                new AgentModelUsage(20L, 10L),
                                step.toolCalls().isEmpty() ? "stop" : "tool_calls",
                                "scripted-eval-v1"
                        ),
                        step.text() == null ? new String[0] : new String[]{step.text()}
                ))
                .toList());
        AgentToolRegistry registry = registry(scenario);
        List<AgentModelMessage> messages = new ArrayList<>();
        messages.add(AgentModelMessage.system("Use only the supplied deterministic tools."));
        messages.add(AgentModelMessage.user(scenario.userMessage()));
        List<String> observedTools = new ArrayList<>();
        int clarifications = 0;
        String finalText = null;

        for (int turn = 0; turn < scenario.steps().size(); turn++) {
            AgentModelResponse result = model.turn(
                    new AgentModelRequest(
                            "scripted-eval-v1",
                            messages,
                            registry.descriptors().stream()
                                    .map(descriptor -> new AgentModelToolDefinition(
                                            descriptor.name(),
                                            descriptor.description(),
                                            descriptor.inputSchemaJson()
                                    ))
                                    .toList(),
                            0,
                            1024
                    ),
                    ignored -> { },
                    () -> false
            );
            if (result.text() != null
                    && result.text().toUpperCase(Locale.ROOT).startsWith("WAITING_FOR_USER:")) {
                clarifications++;
            }
            if (result.toolCalls().isEmpty()) {
                finalText = result.text();
                break;
            }
            messages.add(AgentModelMessage.assistant(result.text(), result.toolCalls()));
            List<AgentModelToolResult> toolResults = result.toolCalls().stream().map(call -> {
                observedTools.add(call.name());
                AgentToolExecutionResult execution = registry.required(call.name()).execute(
                        new AgentToolExecutionContext(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                scenario.userMessage()
                        ),
                        call.argumentsJson()
                );
                return new AgentModelToolResult(call.id(), call.name(), execution.resultJson());
            }).toList();
            messages.add(AgentModelMessage.tools(toolResults));
        }

        assertThat(observedTools).containsSubsequence(scenario.requiredTools().toArray(String[]::new));
        assertThat(observedTools).doesNotContainAnyElementsOf(scenario.forbiddenTools());
        assertThat(registry.descriptors()).extracting(AgentToolDescriptor::name)
                .doesNotContain("complete_checkout");
        assertThat(clarifications).isLessThanOrEqualTo(scenario.maximumClarifications());
        assertThat(finalText).isNotBlank();
        scenario.integrationInvariants().forEach(this::assertIntegrationInvariantExists);
        for (int index = 1; index < model.requests().size(); index++) {
            AgentModelMessage previousResult = model.requests().get(index).messages().getLast();
            assertThat(previousResult.toolResults())
                    .as("scenario %s model turn %s consumes verified tool results", scenario.id(), index + 1)
                    .isNotEmpty();
        }
    }

    private void assertIntegrationInvariantExists(String reference) {
        int separator = reference.lastIndexOf('#');
        assertThat(separator)
                .as("integration invariant reference %s has Class#method form", reference)
                .isPositive();
        String className = reference.substring(0, separator);
        String methodName = reference.substring(separator + 1);
        try {
            Class<?> testClass = Class.forName(
                    className,
                    false,
                    AgentNorthStarScenarioEvaluationTest.class.getClassLoader()
            );
            assertThat(Stream.of(testClass.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
                    .as("referenced Phase 1 invariant %s", reference)
                    .contains(methodName);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Missing referenced Phase 1 invariant " + reference, exception);
        }
    }

    private AgentToolRegistry registry(Scenario scenario) {
        Set<String> names = new LinkedHashSet<>();
        scenario.steps().forEach(step -> step.toolCalls().forEach(call -> names.add(call.name())));
        return new AgentToolRegistry(names.stream().map(ScenarioTool::new).map(AgentTool.class::cast).toList());
    }

    private List<Scenario> scenarios() throws IOException {
        try (InputStream input = AgentNorthStarScenarioEvaluationTest.class.getResourceAsStream(FIXTURE)) {
            if (input == null) {
                throw new IOException("Missing scenario fixture " + FIXTURE);
            }
            return objectMapper.readValue(input, new TypeReference<>() { });
        }
    }

    private static final class ScenarioTool implements AgentTool {

        private final AgentToolDescriptor descriptor;

        private ScenarioTool(String name) {
            AgentToolRisk risk = switch (name) {
                case "prepare_checkout" -> AgentToolRisk.CHECKOUT_PREPARATION;
                case "create_shopping_mission", "evaluate_mission_coverage", "prepare_carts" ->
                        AgentToolRisk.REVERSIBLE_MUTATION;
                default -> AgentToolRisk.READ;
            };
            descriptor = new AgentToolDescriptor(
                    name,
                    "Scenario fixture tool " + name,
                    "{\"type\":\"object\",\"additionalProperties\":true}",
                    "eval-v1",
                    risk
            );
        }

        @Override
        public AgentToolDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
            return AgentToolExecutionResult.read(
                    "{\"verified\":true,\"tool\":\"" + descriptor.name() + "\"}",
                    "Verified " + descriptor.name(),
                    List.of()
            );
        }
    }

    private record Scenario(
            String id,
            String userMessage,
            List<ScenarioStep> steps,
            List<String> requiredTools,
            List<String> forbiddenTools,
            int maximumClarifications,
            List<String> integrationInvariants
    ) {
        private Scenario {
            steps = List.copyOf(steps);
            requiredTools = List.copyOf(requiredTools);
            forbiddenTools = List.copyOf(forbiddenTools);
            integrationInvariants = integrationInvariants == null ? List.of() : List.copyOf(integrationInvariants);
        }
    }

    private record ScenarioStep(String text, List<AgentModelToolCall> toolCalls) {
        private ScenarioStep {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }
    }
}
