package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.constant.AgentToolInvocationStatus;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.entity.AgentToolInvocation;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentToolInvocationRepository;
import com.meant.api.module.agent.service.command.CreateAgentConversationCommand;
import com.meant.api.module.agent.service.command.SubmitAgentTurnCommand;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.user.entity.User;
import com.meant.api.module.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "commerce.agent.enabled=true",
        "commerce.agent.api-key=test-key"
})
class AgentMessageLedgerConcurrencyIT extends PostgresIntegrationTestSupport {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000921");
    private static final int PARALLEL_COMPLETIONS = 4;

    @Autowired private UserRepository userRepository;
    @Autowired private AgentConversationService conversationService;
    @Autowired private AgentTurnService turnService;
    @Autowired private AgentRunService runService;
    @Autowired private AgentToolInvocationService invocationService;
    @Autowired private AgentToolInvocationRepository invocationRepository;
    @Autowired private AgentMessageRepository messageRepository;

    @Test
    void parallelReadToolCompletionsPersistWithoutAStaleRunVersion() throws Exception {
        Instant now = Instant.now();
        userRepository.saveAndFlush(User.builder()
                .id(USER_ID)
                .email("parallel-ledger@example.test")
                .createdAt(now)
                .updatedAt(now)
                .build());
        UUID conversationId = conversationService.create(
                new CreateAgentConversationCommand(USER_ID, "Parallel read ledger")).conversationId();
        UUID runId = turnService.submit(new SubmitAgentTurnCommand(
                USER_ID,
                conversationId,
                "What products are on my shelf?",
                "parallel-read-ledger"
        )).runId();
        UUID executionOwner = runService.claim(runId).orElseThrow();
        List<AgentToolInvocation> invocations = new ArrayList<>();
        for (int index = 0; index < PARALLEL_COMPLETIONS; index++) {
            invocations.add(invocationRepository.save(AgentToolInvocation.builder()
                    .runId(runId)
                    .modelToolCallId("read-" + index)
                    .toolName("read_shelf_" + index)
                    .toolVersion("test-v1")
                    .riskClass(AgentToolRisk.READ)
                    .status(AgentToolInvocationStatus.RUNNING)
                    .argumentsJson("{}")
                    .idempotencyKey("parallel-read-" + index)
                    .createdAt(now)
                    .startedAt(now)
                    .build()));
        }
        invocationRepository.flush();

        CountDownLatch ready = new CountDownLatch(PARALLEL_COMPLETIONS);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(PARALLEL_COMPLETIONS);
        try {
            List<? extends Future<?>> completions = invocations.stream()
                    .map(invocation -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        invocationService.complete(
                                runId,
                                conversationId,
                                invocation.getId(),
                                invocation.getModelToolCallId(),
                                invocation.getToolName(),
                                AgentToolExecutionResult.read("{\"ok\":true}", "Shelf read completed.", List.of()),
                                10,
                                executionOwner
                        );
                        return null;
                    }))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> completion : completions) {
                completion.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(invocationRepository.findByRunIdOrderByCreatedAtAsc(runId))
                .hasSize(PARALLEL_COMPLETIONS)
                .allSatisfy(invocation -> assertThat(invocation.getStatus())
                        .isEqualTo(AgentToolInvocationStatus.COMPLETED));
        assertThat(messageRepository.findByRunIdOrderBySequenceNumberAsc(runId))
                .filteredOn(message -> message.getRole() == AgentMessageRole.TOOL)
                .extracting(message -> message.getSequenceNumber())
                .containsExactly(2L, 3L, 4L, 5L);
    }
}
