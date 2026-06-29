package com.meant.api.plugin.spi;

import java.util.List;

public interface UcpCapability<TRequest, TArguments, TResult> {

    CapabilityId id();

    List<CapabilityAdvertisement> advertisements();

    TArguments buildArguments(TRequest request, NegotiatedCapabilities activeCapabilities);

    TResult parseResponse(UcpToolResponse<?> response);

    default List<String> toolNames() {
        return advertisements().stream()
                .flatMap(advertisement -> advertisement.tools().stream())
                .distinct()
                .toList();
    }
}
