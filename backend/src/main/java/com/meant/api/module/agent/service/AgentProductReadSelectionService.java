package com.meant.api.module.agent.service;

import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.service.dto.AgentProductReadSelection;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.merchant.service.MerchantIntegrationLookupService;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByIdsQuery;
import com.meant.api.module.user.service.UserCanonicalProductDetailService;
import com.meant.api.module.user.service.dto.UserProductDetailResult;
import com.meant.api.module.user.service.query.GetUserCanonicalProductDetailQuery;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentProductReadSelectionService {

    private final AgentContextProfileService profileService;
    private final AgentProductReadReferenceService referenceService;
    private final UserCanonicalProductDetailService detailService;
    private final MerchantIntegrationLookupService integrationLookupService;

    public AgentProductReadSelection select(
            AgentToolExecutionContext context,
            String canonicalProductKey,
            String selectedOfferKey,
            boolean requireLocalMerchant
    ) {
        referenceService.requireProduct(context, canonicalProductKey);
        UserProductDetailResult detail = detailService.get(
                profileService.profile(context.userId()),
                new GetUserCanonicalProductDetailQuery(
                        context.userId(), canonicalProductKey, selectedOfferKey));
        Offer offer = detail.product().offers().stream()
                .filter(candidate -> candidate.key().equals(detail.selectedOfferKey()))
                .findFirst()
                .orElseThrow(AgentException::notFound);
        UUID integrationId = localIntegrationId(offer);
        MerchantIntegrationResult integration = integrationId == null
                ? null
                : integrationLookupService.listByIds(
                                new ListMerchantIntegrationsByIdsQuery(Set.of(integrationId)))
                        .stream()
                        .filter(candidate -> candidate.id().equals(integrationId))
                        .findFirst()
                        .orElse(null);
        if (requireLocalMerchant && integration == null) {
            throw AgentProductReadToolException.invalid(
                    "The selected offer does not support this merchant capability.");
        }
        return new AgentProductReadSelection(detail, offer, integration);
    }

    private UUID localIntegrationId(Offer offer) {
        if (offer.identity().merchantScope().merchantIntegrationFallbackId() != null) {
            return offer.identity().merchantScope().merchantIntegrationFallbackId();
        }
        return offer.provenance().stream()
                .filter(provenance -> provenance.localRouting() != null)
                .map(provenance -> provenance.localRouting().merchantIntegrationId())
                .findFirst()
                .orElse(null);
    }
}
