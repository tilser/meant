package com.meant.api.module.user.service;

import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserFederatedProductSearchStreamEvent;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryEvent;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRequest;
import com.meant.api.module.catalog.service.ExactProductGroupingService;
import com.meant.api.module.catalog.service.FederatedCatalogDiscoveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserFederatedProductSearchStreamService {

    private final UserProductSearchPreparationService preparationService;
    private final FederatedCatalogDiscoveryService federatedDiscoveryService;
    private final ExactProductGroupingService exactProductGroupingService;

    public void stream(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SearchUserProductsCommand command,
            @NotNull Consumer<UserFederatedProductSearchStreamEvent> eventConsumer
    ) {
        stream(profileCommand, command, null, eventConsumer);
    }

    public void stream(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid SearchUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters,
            @NotNull Consumer<UserFederatedProductSearchStreamEvent> eventConsumer
    ) {
        var preparation = preparationService.prepare(profileCommand, command, discoveryFilters);
        federatedDiscoveryService.search(new CatalogDiscoveryRequest(
                preparation.catalogInput().searchQuery(),
                command.merchantId(),
                preparation.fetchLimit(),
                preparation.catalogInput().context(),
                preparation.catalogInput().signals(),
                preparation.catalogInput().filters(),
                preparation.catalogInput().discoveryFilters()
        ), event -> eventConsumer.accept(map(event)));
    }

    private UserFederatedProductSearchStreamEvent map(CatalogDiscoveryEvent event) {
        CanonicalProduct candidate = event.candidate() == null
                ? null
                : exactProductGroupingService.group(List.of(event.candidate())).getFirst();
        return new UserFederatedProductSearchStreamEvent(
                event.type(),
                event.source(),
                event.observationSources(),
                candidate,
                event.failure(),
                event.terminalStatus()
        );
    }
}
