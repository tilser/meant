package com.meant.api.module.agent.service.tool;

import com.meant.api.module.agent.constant.AgentMutationAdmission;
import com.meant.api.module.agent.constant.AgentToolInvocationStatus;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentMutationExecutionLane;
import com.meant.api.module.agent.service.AgentRunService;
import com.meant.api.module.agent.service.dto.AgentExecutedToolCall;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentModelToolResult;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolErrorPayload;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentToolCallExecutor {

    private final AgentToolRegistry registry;
    private final AgentToolAuthorizationPolicy authorizationPolicy;
    private final AgentToolInvocationService invocationService;
    private final AgentRunService runService;
    private final AgentJsonSupport jsonSupport;
    private final AgentToolSchemaValidator schemaValidator;
    private final AgentMutationExecutionLane mutationExecutionLane;
    private final AgentProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AgentExecutedToolCall execute(AgentToolExecutionContext context, AgentModelToolCall call) {
        long started = System.nanoTime();
        AgentTool tool;
        AgentToolDescriptor descriptor;
        try {
            tool = registry.required(call.name());
            descriptor = tool.descriptor();
        } catch (AgentException exception) {
            invocationService.rejectUnregistered(
                    context.runId(), call, properties.toolVersion(), context.executionOwner());
            return new AgentExecutedToolCall(
                    new AgentModelToolResult(
                            call.id(),
                            call.name(),
                            jsonSupport.write(new AgentToolErrorPayload(
                                    false,
                                    "tool_not_allowed",
                                    "That tool is not available.",
                                    false
                            ))
                    ),
                    false
            );
        }
        if (!authorizationPolicy.authorized(context, descriptor)) {
            invocationService.rejectUnauthorized(context.runId(), call, descriptor, context.executionOwner());
            return new AgentExecutedToolCall(
                    new AgentModelToolResult(
                            call.id(),
                            call.name(),
                            jsonSupport.write(new AgentToolErrorPayload(
                                    false,
                                    "authorization_required",
                                    "That action needs a clearer instruction from the user.",
                                    false
                            ))
                    ),
                    false
            );
        }

        String arguments;
        try {
            arguments = jsonSupport.validateArguments(call.argumentsJson());
            schemaValidator.validate(descriptor.inputSchemaJson(), arguments);
        } catch (RuntimeException exception) {
            invocationService.rejectInvalid(
                    context.runId(),
                    call,
                    descriptor,
                    call.argumentsJson(),
                    context.executionOwner()
            );
            boolean retryable = exception instanceof AgentException;
            String message = retryable
                    ? ((AgentException) exception).getSafeMessage()
                    : "The tool arguments were invalid.";
            return new AgentExecutedToolCall(
                    new AgentModelToolResult(
                            call.id(),
                            call.name(),
                            jsonSupport.write(new AgentToolErrorPayload(
                                    false,
                                    "invalid_arguments",
                                    message,
                                    retryable
                            ))
                    ),
                    false
            );
        }
        if (!authorizationPolicy.authorizedInvocation(context, descriptor, arguments)) {
            invocationService.rejectUnauthorized(context.runId(), call, descriptor, context.executionOwner());
            return new AgentExecutedToolCall(
                    new AgentModelToolResult(
                            call.id(),
                            call.name(),
                            jsonSupport.write(new AgentToolErrorPayload(
                                    false,
                                    "authorization_required",
                                    "That action could not be resolved to one exact current conversation item.",
                                    false
                            ))
                    ),
                    false
            );
        }

        String idempotencyKey = idempotencyKey(context, descriptor, call, arguments);
        var reservation = invocationService.reserve(
                context.runId(), call, descriptor, arguments, idempotencyKey, context.executionOwner());
        if (!reservation.execute()) {
            return new AgentExecutedToolCall(
                    new AgentModelToolResult(call.id(), call.name(), reservation.completedResultJson()),
                    true,
                    reservation.waitingForUserMessage()
            );
        }
        if (cancellationRequested(context)) {
            invocationService.fail(
                    context.runId(),
                    reservation.invocationId(),
                    call.id(),
                    call.name(),
                    reservation.reconciliationRetry()
                            ? AgentToolInvocationStatus.UNCERTAIN
                            : AgentToolInvocationStatus.CANCELLED,
                    reservation.reconciliationRetry() ? "reconciliation_cancelled" : "cancelled",
                    reservation.reconciliationRetry()
                            ? "The retry was cancelled; the earlier mutation outcome is still unconfirmed."
                            : "The run was cancelled before the tool started.",
                    elapsedMilliseconds(started),
                    context.executionOwner()
            );
            throw new CancellationException("Agent run was cancelled before tool execution");
        }

        invocationService.start(
                context.runId(), reservation.invocationId(), call.id(), call.name(), context.executionOwner());
        AgentToolExecutionContext reservedContext = context.withIdempotencyKey(reservation.invocationId());
        AgentMutationExecutionLane.Attempt attempt =
                mutationExecutionLane.newAttempt(properties.toolDeadline());
        Future<AgentToolExecutionResult> future = executor.submit(() -> mutationExecutionLane.execute(
                context.userId(),
                descriptor.riskClass(),
                attempt,
                () -> tool.execute(reservedContext, arguments)
        ));
        try {
            AgentToolExecutionResult result = future.get(
                    attempt.remainingNanos(),
                    TimeUnit.NANOSECONDS
            );
            invocationService.complete(
                    context.runId(),
                    context.conversationId(),
                    reservation.invocationId(),
                    call.id(),
                    call.name(),
                    result,
                    elapsedMilliseconds(started),
                    context.executionOwner()
            );
            return new AgentExecutedToolCall(
                    new AgentModelToolResult(call.id(), call.name(), jsonSupport.bounded(result.resultJson())),
                    true,
                    result.waitingForUserMessage()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            boolean definitelyNotStarted = attempt.cancelBeforeStart();
            future.cancel(true);
            invocationService.fail(
                    context.runId(),
                    reservation.invocationId(),
                    call.id(),
                    call.name(),
                    reservation.reconciliationRetry()
                            ? AgentToolInvocationStatus.UNCERTAIN
                            : (definitelyNotStarted || descriptor.riskClass() == AgentToolRisk.READ
                                    ? AgentToolInvocationStatus.CANCELLED
                                    : AgentToolInvocationStatus.UNCERTAIN),
                    "interrupted",
                    definitelyNotStarted && !reservation.reconciliationRetry()
                            ? "The tool was interrupted before it started."
                            : "The tool was interrupted before its outcome could be confirmed.",
                    elapsedMilliseconds(started),
                    context.executionOwner()
            );
            throw new CancellationException("Agent tool execution was interrupted");
        } catch (TimeoutException exception) {
            boolean definitelyNotStarted = attempt.cancelBeforeStart();
            future.cancel(true);
            return failed(
                    context,
                    call,
                    reservation.invocationId(),
                    started,
                    reservation.reconciliationRetry()
                            ? AgentToolInvocationStatus.UNCERTAIN
                            : (definitelyNotStarted || descriptor.riskClass() == AgentToolRisk.READ
                                    ? AgentToolInvocationStatus.FAILED
                                    : AgentToolInvocationStatus.UNCERTAIN),
                    definitelyNotStarted && !reservation.reconciliationRetry()
                            ? AgentMutationAdmission.FAILURE_CLASSIFICATION
                            : "timeout",
                    definitelyNotStarted && !reservation.reconciliationRetry()
                            ? "The tool could not start before its deadline. Try again."
                            : "The tool timed out. Try again.",
                    true
            );
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof TimeoutException) {
                return failed(
                        context,
                        call,
                        reservation.invocationId(),
                        started,
                        reservation.reconciliationRetry()
                                ? AgentToolInvocationStatus.UNCERTAIN
                                : AgentToolInvocationStatus.FAILED,
                        reservation.reconciliationRetry()
                                ? "timeout"
                                : AgentMutationAdmission.FAILURE_CLASSIFICATION,
                        reservation.reconciliationRetry()
                                ? "The earlier mutation outcome is still unconfirmed. Try again."
                                : "The tool could not start before its wait limit. Try again.",
                        true
                );
            }
            boolean uncertainMutation = descriptor.riskClass() != AgentToolRisk.READ
                    && !(cause instanceof AgentException);
            String message = cause instanceof AgentException agentException
                    ? agentException.getSafeMessage()
                    : "The tool could not complete. Try again.";
            return failed(
                    context,
                    call,
                    reservation.invocationId(),
                    started,
                    uncertainMutation ? AgentToolInvocationStatus.UNCERTAIN : AgentToolInvocationStatus.FAILED,
                    cause instanceof AgentException ? "domain_error" : "tool_error",
                    message,
                    uncertainMutation
            );
        }
    }

    private AgentExecutedToolCall failed(
            AgentToolExecutionContext context,
            AgentModelToolCall call,
            UUID invocationId,
            long started,
            AgentToolInvocationStatus status,
            String classification,
            String safeMessage,
            boolean retryable
    ) {
        invocationService.fail(
                context.runId(),
                invocationId,
                call.id(),
                call.name(),
                status,
                classification,
                safeMessage,
                elapsedMilliseconds(started),
                context.executionOwner()
        );
        return new AgentExecutedToolCall(
                new AgentModelToolResult(
                        call.id(),
                        call.name(),
                        jsonSupport.write(new AgentToolErrorPayload(false, classification, safeMessage, retryable))
                ),
                false
        );
    }

    private boolean cancellationRequested(AgentToolExecutionContext context) {
        return context.executionOwner() == null
                ? runService.cancellationRequested(context.runId())
                : runService.cancellationRequested(context.runId(), context.executionOwner());
    }

    private String idempotencyKey(
            AgentToolExecutionContext context,
            AgentToolDescriptor descriptor,
            AgentModelToolCall call,
            String arguments
    ) {
        String scope = descriptor.riskClass() == AgentToolRisk.READ
                ? context.runId() + ":" + call.id()
                : context.conversationId() + ":" + context.triggeringMessageId();
        return "agent:" + sha256(scope + ":" + descriptor.version() + ":" + descriptor.name() + ":" + arguments);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private long elapsedMilliseconds(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
