package com.meant.api.module.agent.service;

import com.meant.api.common.constant.ApiErrorCode;
import com.meant.api.common.exception.ApiException;
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
        Future<AgentToolExecutionResult> future = executor.submit(() -> tool.execute(
                new AgentToolExecutionContext(
                        command.userId(),
                        command.conversationId(),
                        null,
                        reservation.actionId(),
                        command.summary(),
                        reservation.actionId()
                ).withBuyerIp(command.buyerIp())
                        .withMerchantId(reservation.merchantId()),
                arguments
        ));
        try {
            AgentToolExecutionResult result = future.get(properties.toolDeadline().toMillis(), TimeUnit.MILLISECONDS);
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
            future.cancel(true);
            persistenceService.fail(
                    reservation.actionId(),
                    uncertainStatus(tool.descriptor().riskClass()),
                    "The action was interrupted before its outcome could be confirmed."
            );
            metrics.tool(
                    tool.descriptor().name(),
                    tool.descriptor().riskClass(),
                    "cancelled",
                    elapsedMilliseconds(started)
            );
            throw AgentException.actionUncertain(
                    HttpStatus.CONFLICT,
                    "The action was interrupted before its outcome could be confirmed. Try again."
            );
        } catch (TimeoutException exception) {
            future.cancel(true);
            persistenceService.fail(
                    reservation.actionId(),
                    uncertainStatus(tool.descriptor().riskClass()),
                    "The action timed out before its outcome could be confirmed."
            );
            metrics.tool(
                    tool.descriptor().name(),
                    tool.descriptor().riskClass(),
                    "timeout",
                    elapsedMilliseconds(started)
            );
            throw AgentException.actionUncertain(
                    HttpStatus.REQUEST_TIMEOUT,
                    "The action timed out. Try again."
            );
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
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

    private AgentUserActionStatus uncertainStatus(AgentToolRisk risk) {
        return risk == AgentToolRisk.READ ? AgentUserActionStatus.FAILED : AgentUserActionStatus.UNCERTAIN;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
