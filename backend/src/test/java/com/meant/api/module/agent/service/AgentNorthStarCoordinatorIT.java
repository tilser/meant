package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.constant.AgentToolInvocationStatus;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentProductInteractionRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.repository.AgentToolInvocationRepository;
import com.meant.api.module.agent.service.command.CreateAgentConversationCommand;
import com.meant.api.module.agent.service.command.SubmitAgentTurnCommand;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentModelRequest;
import com.meant.api.module.agent.service.dto.AgentModelResponse;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentModelUsage;
import com.meant.api.module.agent.service.port.AgentModelGateway;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "commerce.agent.enabled=true",
        "commerce.agent.api-key=test-key",
        "commerce.agent.event-poll-interval=10ms"
})
class AgentNorthStarCoordinatorIT extends PostgresIntegrationTestSupport {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final String PRODUCT_KEY = "canonical:product:agent-eval-1";
    private static final String OFFER_KEY = "offer:agent-eval-1";

    @Autowired private UserRepository userRepository;
    @Autowired private AgentConversationService conversationService;
    @Autowired private AgentTurnService turnService;
    @Autowired private AgentRunCoordinator coordinator;
    @Autowired private AgentArtifactService artifactService;
    @Autowired private AgentRunRepository runRepository;
    @Autowired private AgentMessageRepository messageRepository;
    @Autowired private AgentToolInvocationRepository invocationRepository;
    @Autowired private AgentArtifactReferenceRepository artifactRepository;
    @Autowired private AgentProductInteractionRepository interactionRepository;

    @MockitoBean
    private AgentModelGateway modelGateway;

    @BeforeEach
    void persistUser() {
        Instant now = Instant.now();
        userRepository.saveAndFlush(User.builder()
                .id(USER_ID)
                .email("agent-eval@example.test")
                .firstName("Agent")
                .surname("Evaluator")
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Test
    void scriptedTurnRunsActualPolicyExecutorToolAndDurableLedger() throws InterruptedException {
        var conversation = conversationService.create(
                new CreateAgentConversationCommand(USER_ID, "Deterministic agent evaluation"));
        artifactService.persist(
                conversation.conversationId(),
                null,
                null,
                null,
                List.of(new AgentArtifact(
                        AgentArtifactType.PRODUCT,
                        1,
                        PRODUCT_KEY,
                        "Gray trail shoe",
                        PRODUCT_KEY,
                        OFFER_KEY,
                        null,
                        null,
                        null,
                        null,
                        "{\"name\":\"Gray trail shoe\",\"offerKey\":\"" + OFFER_KEY + "\"}"
                ))
        );

        AtomicInteger modelTurn = new AtomicInteger();
        List<AgentModelRequest> requests = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            AgentModelRequest request = invocation.getArgument(0);
            requests.add(request);
            if (modelTurn.getAndIncrement() == 0) {
                return new AgentModelResponse(
                        "",
                        List.of(new AgentModelToolCall(
                                "pin-first",
                                "pin_product",
                                "{\"canonicalProductKey\":\"" + PRODUCT_KEY
                                        + "\",\"offerKey\":\"" + OFFER_KEY + "\"}"
                        )),
                        new AgentModelUsage(40L, 10L),
                        "tool_calls",
                        "scripted-agent-eval"
                );
            }
            return new AgentModelResponse(
                    "Pinned the first product.",
                    List.of(),
                    new AgentModelUsage(60L, 12L),
                    "stop",
                    "scripted-agent-eval"
            );
        }).when(modelGateway).turn(any(), any(), any());

        var accepted = turnService.submit(new SubmitAgentTurnCommand(
                USER_ID,
                conversation.conversationId(),
                "Pin the first one",
                "north-star-pin-first"
        ));
        coordinator.schedule(accepted.runId());

        verify(modelGateway, timeout(5_000).times(2)).turn(any(), any(), any());
        awaitTerminalRun(accepted.runId());

        var run = runRepository.findById(accepted.runId()).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(run.getIterationCount()).isEqualTo(2);
        assertThat(run.getToolInvocationCount()).isEqualTo(1);

        assertThat(invocationRepository.findByRunIdOrderByCreatedAtAsc(run.getId()))
                .singleElement()
                .satisfies(tool -> {
                    assertThat(tool.getToolName()).isEqualTo("pin_product");
                    assertThat(tool.getStatus()).isEqualTo(AgentToolInvocationStatus.COMPLETED);
                    assertThat(tool.getCanonicalProductKey()).isEqualTo(PRODUCT_KEY);
                    assertThat(tool.getOfferKey()).isEqualTo(OFFER_KEY);
                });
        assertThat(interactionRepository.findActiveByUserId(USER_ID, PageRequest.of(0, 10)))
                .singleElement()
                .satisfies(interaction -> {
                    assertThat(interaction.getUserId()).isEqualTo(USER_ID);
                    assertThat(interaction.getCanonicalProductKey()).isEqualTo(PRODUCT_KEY);
                    assertThat(interaction.isPinned()).isTrue();
                    assertThat(interaction.getPinnedOfferKey()).isEqualTo(OFFER_KEY);
                });
        assertThat(artifactRepository.findByRunIdOrderByCreatedAtAscOrdinalAsc(run.getId()))
                .singleElement()
                .satisfies(artifact -> {
                    assertThat(artifact.getArtifactType()).isEqualTo(AgentArtifactType.PRODUCT_STATE);
                    assertThat(artifact.getMessageId()).isNotNull();
                    assertThat(artifact.getToolInvocationId()).isNotNull();
                });
        assertThat(messageRepository.findByRunIdOrderBySequenceNumberAsc(run.getId()))
                .extracting(message -> message.getRole())
                .containsExactly(AgentMessageRole.USER, AgentMessageRole.TOOL, AgentMessageRole.ASSISTANT);
        assertThat(messageRepository.findByRunIdOrderBySequenceNumberAsc(run.getId()).getLast().getTextContent())
                .isEqualTo("Pinned the first product.");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).messages().getLast().toolResults())
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.toolName()).isEqualTo("pin_product");
                    assertThat(result.resultJson()).contains("\"pinned\":true");
                });
    }

    private void awaitTerminalRun(UUID runId) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (runRepository.findById(runId)
                    .map(run -> run.getStatus().terminal())
                    .orElse(false)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Agent run did not reach a terminal state");
    }
}
