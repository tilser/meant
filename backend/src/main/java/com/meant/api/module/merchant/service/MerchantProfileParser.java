package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.service.dto.MerchantProfileData;
import com.meant.api.module.merchant.service.dto.StorePolicyFaqEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class MerchantProfileParser {

    private static final List<String> SECTION_LABELS = List.of(
            "Description:",
            "About us:",
            "Target audience:",
            "Categories:",
            "Popular searches:"
    );

    public MerchantProfileData parse(String domain, StorePolicyFaqEntry entry) {
        if (entry.question() == null || entry.answer() == null) {
            throw new MerchantEnrichmentException("Store profile entry must include question and answer");
        }

        String question = sanitizeText(entry.question());
        String answer = sanitizeText(entry.answer());
        Map<String, String> sections = extractSections(answer);
        return new MerchantProfileData(
                domain,
                sectionOrEmpty(sections, "Description:"),
                sectionOrEmpty(sections, "About us:"),
                sectionOrEmpty(sections, "Target audience:"),
                question,
                answer,
                splitList(sectionOrEmpty(sections, "Categories:")),
                splitList(sectionOrEmpty(sections, "Popular searches:"))
        );
    }

    private Map<String, String> extractSections(String answer) {
        String normalizedAnswer = answer.replace("\r\n", "\n").replace('\r', '\n');
        Map<String, Integer> labelPositions = new LinkedHashMap<>();
        for (String label : SECTION_LABELS) {
            int index = normalizedAnswer.indexOf(label);
            if (index >= 0) {
                labelPositions.put(label, index);
            }
        }

        Map<String, String> sections = new LinkedHashMap<>();
        List<Map.Entry<String, Integer>> orderedLabels = labelPositions.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .toList();
        for (int i = 0; i < orderedLabels.size(); i++) {
            Map.Entry<String, Integer> current = orderedLabels.get(i);
            int start = current.getValue() + current.getKey().length();
            int end = i + 1 < orderedLabels.size() ? orderedLabels.get(i + 1).getValue() : normalizedAnswer.length();
            sections.put(current.getKey(), normalizedAnswer.substring(start, end).trim());
        }
        return sections;
    }

    private String sectionOrEmpty(Map<String, String> sections, String label) {
        String value = sections.get(label);
        if (value == null || value.isBlank()) {
            return "";
        }
        return value;
    }

    private List<String> splitList(String value) {
        return Stream.of(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String sanitizeText(String value) {
        return value.replace("\u0000", "");
    }
}
