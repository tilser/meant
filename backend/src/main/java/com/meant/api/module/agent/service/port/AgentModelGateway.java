package com.meant.api.module.agent.service.port;

import com.meant.api.module.agent.service.dto.AgentModelRequest;
import com.meant.api.module.agent.service.dto.AgentModelResponse;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface AgentModelGateway {

    AgentModelResponse turn(
            AgentModelRequest request,
            Consumer<String> textDeltaConsumer,
            BooleanSupplier cancellationRequested
    );
}
