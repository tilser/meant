package com.meant.api.module.agent.service;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
import com.meant.api.module.agent.constant.AgentMutationAdmission;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.constant.AgentUserActionStatus;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.command.RecordAgentUserActionCommand;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.AgentUserActionResult;
import com.meant.api.module.agent.service.tool.AgentTool;
import com.meant.api.module.agent.service.tool.AgentToolRegistry;
import com.meant.api.module.agent.service.tool.AgentToolSchemaValidator;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class AgentUserActionService {

    private final AgentToolRegistry toolRegistry;
    private final AgentUserActionPersistenceService persistenceService;
    private final AgentJsonSupport jsonSupport;
    private final AgentToolSchemaValidator schemaValidator;
    private final AgentMetrics metrics;
    private final AgentMutationExecutionLane mutationExecutionLane;
    private final AgentProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AgentUserActionResult perform(@Valid RecordAgentUserActionCommand command) {
        long started = System.nanoTime();
        if (!properties.enabled()) {
            throw AgentException.disabled();
        }
        AgentTool tool = toolRegistry.required(command.toolName());
        String arguments = jsonSupport.validateArguments(command.argumentsJson());
        schemaValidator.validate(tool.descriptor().inputSchemaJson(), arguments);
        var reservation = persistenceService.reserve(command, arguments, tool.descriptor().version());
        if (!reservation.execute()) {
            metrics.tool(tool.descriptor().name(), tool.descriptor().riskClass(), "replayed", 0);
            return reservation.completedResult();
        }
        persistenceService.start(reservation.actionId());
        AgentToolExecutionContext context = new AgentToolExecutionContext(
                command.userId(),
                command.conversationId(),
                null,
                reservation.actionId(),
                command.summary(),
                reservation.actionId()
        ).withAnonymousUser(command.anonymousUser())
                .withBuyerIp(command.buyerIp())
                .withMerchantId(reservation.merchantId());
        AgentMutationExecutionLane.Attempt attempt =
                mutationExecutionLane.newAttempt(properties.toolDeadline());
        Future<AgentToolExecutionResult> future = executor.submit(() -> mutationExecutionLane.execute(
                command.userId(),
                tool.descriptor().riskClass(),
                attempt,
                () -> tool.execute(context, arguments)
        ));
        try {
            AgentToolExecutionResult result = future.get(attempt.remainingNanos(), TimeUnit.NANOSECONDS);
            AgentUserActionResult completed = persistenceService.complete(reservation.actionId(), command, result);
            metrics.tool(
                    tool.descriptor().name(),
                    tool.descriptor().riskClass(),
                    "completed",
                    elapsedMilliseconds(started)
            );
            return completed;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            boolean definitelyNotStarted = attempt.cancelBeforeStart();
            future.cancel(true);
            persistenceService.fail(
                    reservation.actionId(),
                    unconfirmedStatus(
                            tool.descriptor().riskClass(),
                            definitelyNotStarted,
                            reservation.reconciliationRetry()
                    ),
                    definitelyNotStarted && !reservation.reconciliationRetry()
                            ? "The action was interrupted before it started."
                            : "The action was interrupted before its outcome could be confirmed."
            );
            metrics.tool(
                    tool.descriptor().name(),
                    tool.descriptor().riskClass(),
                    "cancelled",
                    elapsedMilliseconds(started)
            );
            if (definitelyNotStarted && !reservation.reconciliationRetry()) {
                throw AgentException.conflict("The action did not start. Try again.");
            }
            throw AgentException.actionUncertain(
                    HttpStatus.CONFLICT,
                    "The action was interrupted before its outcome could be confirmed. Try again."
            );
        } catch (TimeoutException exception) {
            boolean definitelyNotStarted = attempt.cancelBeforeStart();
            future.cancel(true);
            persistenceService.fail(
                    reservation.actionId(),
                    unconfirmedStatus(
                            tool.descriptor().riskClass(),
                            definitelyNotStarted,
                            reservation.reconciliationRetry()
                    ),
                    definitelyNotStarted && !reservation.reconciliationRetry()
                            ? AgentMutationAdmission.USER_ACTION_SAFE_MESSAGE
                            : "The action timed out before its outcome could be confirmed."
            );
            metrics.tool(
                    tool.descriptor().name(),
                    tool.descriptor().riskClass(),
                    "timeout",
                    elapsedMilliseconds(started)
            );
            if (definitelyNotStarted && !reservation.reconciliationRetry()) {
                throw AgentException.conflict("The action could not start in time. Try again.");
            }
            throw AgentException.actionUncertain(
                    HttpStatus.REQUEST_TIMEOUT,
                    "The action timed out. Try again."
            );
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof TimeoutException) {
                persistenceService.fail(
                        reservation.actionId(),
                        reservation.reconciliationRetry()
                                ? AgentUserActionStatus.UNCERTAIN
                                : AgentUserActionStatus.FAILED,
                        reservation.reconciliationRetry()
                                ? "The earlier action outcome is still unconfirmed."
                                : AgentMutationAdmission.USER_ACTION_SAFE_MESSAGE
                );
                metrics.tool(
                        tool.descriptor().name(),
                        tool.descriptor().riskClass(),
                        "timeout",
                        elapsedMilliseconds(started)
                );
                if (reservation.reconciliationRetry()) {
                    throw AgentException.actionUncertain(
                            HttpStatus.REQUEST_TIMEOUT,
                            "The earlier action outcome is still uncertain. Try again."
                    );
                }
                throw AgentException.conflict("The action could not start in time. Try again.");
            }
            boolean uncertainMutation = outcomeUncertain(tool.descriptor().riskClass(), cause);
            String safeMessage = cause instanceof ApiException apiException
                    ? apiException.getSafeMessage()
                    : "The action could not complete. Try again.";
            persistenceService.fail(
                    reservation.actionId(),
                    uncertainMutation ? AgentUserActionStatus.UNCERTAIN : AgentUserActionStatus.FAILED,
                    safeMessage
            );
            metrics.tool(
                    tool.descriptor().name(),
                    tool.descriptor().riskClass(),
                    "failed",
                    elapsedMilliseconds(started)
            );
            if (uncertainMutation) {
                HttpStatus status = cause instanceof ApiException apiException
                        ? HttpStatus.valueOf(apiException.getStatus().value())
                        : HttpStatus.BAD_REQUEST;
                throw AgentException.actionUncertain(status, safeMessage, cause);
            }
            if (cause instanceof RuntimeException runtimeException && cause instanceof ApiException) {
                throw runtimeException;
            }
            throw new AgentException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, safeMessage, cause);
        }
    }

    private boolean outcomeUncertain(AgentToolRisk risk, Throwable cause) {
        if (risk == AgentToolRisk.READ) {
            return false;
        }
        if (!(cause instanceof ApiException apiException)) {
            return true;
        }
        return apiException.getStatus().value() == HttpStatus.REQUEST_TIMEOUT.value()
                || apiException.getStatus().is5xxServerError();
    }

    private long elapsedMilliseconds(long started) {
        return java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private AgentUserActionStatus unconfirmedStatus(
            AgentToolRisk risk,
            boolean definitelyNotStarted,
            boolean reconciliationRetry
    ) {
        return !reconciliationRetry && (definitelyNotStarted || risk == AgentToolRisk.READ)
                ? AgentUserActionStatus.FAILED
                : AgentUserActionStatus.UNCERTAIN;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
