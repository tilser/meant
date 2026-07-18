package com.meant.api.module.agent.support;

import com.meant.api.module.agent.service.dto.AgentModelRequest;
import com.meant.api.module.agent.service.dto.AgentModelResponse;
import com.meant.api.module.agent.service.port.AgentModelGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Deterministic model harness used by agent loop and scenario tests without network access. */
public final class ScriptedAgentModelGateway implements AgentModelGateway {

    private final ConcurrentLinkedQueue<Step> steps;
    private final List<AgentModelRequest> requests = java.util.Collections.synchronizedList(new ArrayList<>());

    public ScriptedAgentModelGateway(List<Step> steps) {
        this.steps = new ConcurrentLinkedQueue<>(steps);
    }

    @Override
    public AgentModelResponse turn(
            AgentModelRequest request,
            Consumer<String> textDeltaConsumer,
            BooleanSupplier cancellationRequested
    ) {
        requests.add(request);
        if (cancellationRequested.getAsBoolean()) {
            throw new java.util.concurrent.CancellationException("Scripted model turn cancelled");
        }
        Step step = steps.poll();
        if (step == null) {
            throw new IllegalStateException("The scripted model has no response left");
        }
        if (step.failure() != null) {
            throw step.failure();
        }
        for (String delta : step.deltas()) {
            if (cancellationRequested.getAsBoolean()) {
                throw new java.util.concurrent.CancellationException("Scripted model turn cancelled");
            }
            textDeltaConsumer.accept(delta);
        }
        return step.response();
    }

    public List<AgentModelRequest> requests() {
        return List.copyOf(requests);
    }

    public static Step response(AgentModelResponse response, String... deltas) {
        return new Step(response, List.of(deltas), null);
    }

    public static Step failure(RuntimeException failure) {
        return new Step(null, List.of(), failure);
    }

    public record Step(
            AgentModelResponse response,
            List<String> deltas,
            RuntimeException failure
    ) {

        public Step {
            deltas = List.copyOf(deltas);
        }
    }
}
