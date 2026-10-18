package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.enums.FilterMode;
import com.dinhluong.dlmstore.dto.GeminiFilterCriteria;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FilterMergeService {
    public static final Set<String> FIELDS = Set.of("brands", "osTypes", "networks", "minPrice", "maxPrice",
            "minBattery", "minScreenSize", "searchKeyword", "ram", "rom", "categorySlug", "color", "chip",
            "camera", "inStockOnly", "usageGoal");
    private final ObjectMapper mapper;

    public Map<String, Object> merge(Map<String, Object> previous, GeminiFilterCriteria criteria, boolean newTopic) {
        Map<String, Object> result = new LinkedHashMap<>(previous == null ? Map.of() : previous);
        if (newTopic || criteria.getFilterMode() == FilterMode.RESET || criteria.getFilterMode() == FilterMode.REPLACE) {
            FIELDS.forEach(result::remove);
        }
        if (criteria.getClearFields() != null) criteria.getClearFields().forEach(result::remove);
        Map<String, Object> extracted = mapper.convertValue(criteria, new TypeReference<>() { });
        extracted.forEach((key, value) -> {
            if (FIELDS.contains(key) && value != null && !(value instanceof List<?> list && list.isEmpty())) {
                result.put(key, value);
            }
        });
        BigDecimal min = decimal(result.get("minPrice")), max = decimal(result.get("maxPrice"));
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("Khoảng giá mới mâu thuẫn với điều kiện đang giữ; hãy bỏ hoặc đổi cận giá cũ.");
        }
        return result;
    }

    public GeminiFilterCriteria effective(Map<String, Object> context, GeminiFilterCriteria action) {
        var effective = mapper.convertValue(context, GeminiFilterCriteria.class);
        effective.setIntent(action.getIntent());
        effective.setTargetProductName(action.getTargetProductName());
        effective.setCompareProductNames(action.getCompareProductNames());
        effective.setRankingCriterion(action.getRankingCriterion());
        effective.setResultLimit(action.getResultLimit());
        effective.setResultPage(action.getResultPage());
        return effective;
    }
    private static BigDecimal decimal(Object value) {
        return value instanceof Number number ? new BigDecimal(number.toString()) : null;
    }
}
