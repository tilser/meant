package com.meant.api.module.location.service.port;

import com.meant.api.module.location.service.dto.LocationSuggestion;
import java.util.List;

public interface LocationSearchProvider {

    List<LocationSuggestion> search(String query, int limit, String language);

    LocationSuggestion resolve(String id, String language);
}
