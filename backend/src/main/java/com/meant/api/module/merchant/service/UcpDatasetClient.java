package com.meant.api.module.merchant.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class UcpDatasetClient {

    private final RestClient restClient;
    private final CrawlingProperties crawlingProperties;

    public UcpDatasetClient(RestClient.Builder restClientBuilder, CrawlingProperties crawlingProperties) {
        this.restClient = restClientBuilder.build();
        this.crawlingProperties = crawlingProperties;
    }

    public List<HuggingFaceDatasetRow> fetchAllRows() {
        List<HuggingFaceDatasetRow> rows = new ArrayList<>();
        int offset = 0;
        int pageSize = crawlingProperties.getUcpDatasetPageSize();

        while (true) {
            HuggingFaceRowsResponse response = fetchPage(offset, pageSize);
            if (response.rows() == null || response.rows().isEmpty()) {
                return rows;
            }

            rows.addAll(response.rows());
            offset += pageSize;

            if (offset >= response.numRowsTotal()) {
                return rows;
            }
        }
    }

    private HuggingFaceRowsResponse fetchPage(int offset, int pageSize) {
        return restClient.get()
                .uri(URI.create(pageUri(offset, pageSize)))
                .retrieve()
                .body(HuggingFaceRowsResponse.class);
    }

    private String pageUri(int offset, int pageSize) {
        String rowsUrl = crawlingProperties.getUcpDatasetRowsUrl();
        int queryStart = rowsUrl.indexOf('?');
        if (queryStart == -1) {
            return rowsUrl + "?offset=" + offset + "&length=" + pageSize;
        }

        String baseUrl = rowsUrl.substring(0, queryStart);
        String query = rowsUrl.substring(queryStart + 1);
        String filteredQuery = Arrays.stream(query.split("&"))
                .filter(parameter -> !parameter.startsWith("offset="))
                .filter(parameter -> !parameter.startsWith("length="))
                .collect(Collectors.joining("&"));
        String pagingQuery = "offset=" + offset + "&length=" + pageSize;
        if (filteredQuery.isBlank()) {
            return baseUrl + "?" + pagingQuery;
        }
        return baseUrl + "?" + filteredQuery + "&" + pagingQuery;
    }
}
