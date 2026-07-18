package com.meant.api.module.agent.service.dto;

import java.util.UUID;

public record GetOrderAgentToolInput(UUID orderId, Boolean refresh) {
}
