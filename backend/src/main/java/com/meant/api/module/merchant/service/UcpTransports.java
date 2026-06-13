package com.meant.api.module.merchant.service;

import java.util.List;

record UcpTransports(List<String> names) {

    UcpTransports {
        names = List.copyOf(names);
    }
}
