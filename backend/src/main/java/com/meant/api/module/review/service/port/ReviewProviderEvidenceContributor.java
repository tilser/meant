package com.meant.api.module.review.service.port;

import com.meant.api.module.review.constant.ReviewProviderType;
import java.util.List;

public interface ReviewProviderEvidenceContributor {

    List<String> evidencePatterns(ReviewProviderType provider);
}
