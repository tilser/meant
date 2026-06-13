package com.meant.api.module.merchant.service.dto;

import java.util.List;

public record UcpTransports(List<String> names) {

    public UcpTransports {
        names = List.copyOf(names);
    }
}
