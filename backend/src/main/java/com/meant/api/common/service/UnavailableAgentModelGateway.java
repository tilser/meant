package com.meant.api.common.service;

import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.service.dto.AgentModelRequest;
import com.meant.api.module.agent.service.dto.AgentModelResponse;
import com.meant.api.module.agent.service.port.AgentModelGateway;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class UnavailableAgentModelGateway implements AgentModelGateway {

    @Override
    public AgentModelResponse turn(
            AgentModelRequest request,
            Consumer<String> textDeltaConsumer,
            BooleanSupplier cancellationRequested
    ) {
        throw AgentException.disabled();
    }
}
