package com.meant.api.plugin.transport.registry;

import com.meant.api.plugin.spi.CapabilityId;

public class DuplicateUcpToolException extends RuntimeException {

    public DuplicateUcpToolException(String toolName, CapabilityId existingCapability, CapabilityId duplicateCapability) {
        super("UCP tool " + toolName + " is advertised by both "
                + existingCapability + " and " + duplicateCapability);
    }
}
