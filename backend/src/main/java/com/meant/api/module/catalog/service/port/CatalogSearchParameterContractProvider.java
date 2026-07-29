package com.meant.api.module.catalog.service.port;

/**
 * Supplies the currently usable catalog-search parameter contract to request qualification.
 *
 * <p>The owning provider is responsible for validating its live transport contract before
 * advertising parameters through this provider-neutral boundary.</p>
 */
@FunctionalInterface
public interface CatalogSearchParameterContractProvider {

    String currentSearchParameterContract();
}
