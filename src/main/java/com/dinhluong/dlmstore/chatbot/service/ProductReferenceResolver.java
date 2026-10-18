package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.ConversationMemory;
import com.dinhluong.dlmstore.chatbot.enums.ReferenceType;
import com.dinhluong.dlmstore.dto.GeminiFilterCriteria;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ProductReferenceResolver {
    public record Resolution(List<Long> ids, boolean referenced, String question) { }

    public Resolution resolve(GeminiFilterCriteria criteria, ConversationMemory memory,
                              ConversationMemory.Topic topic) {
        ReferenceType type = criteria.getReferenceType() == null ? ReferenceType.NONE : criteria.getReferenceType();
        if ((type == ReferenceType.NONE || type == ReferenceType.PRODUCT_BY_NAME) && hasNames(criteria)) {
            return new Resolution(List.of(), false, null);
        }
        if (type == ReferenceType.NONE && !Set.of("DETAIL", "COMPARE", "EXPLAIN", "RANK", "RECALL")
                .contains(criteria.getIntent())) return new Resolution(List.of(), false, null);
        if (type == ReferenceType.CONVERSATION_HISTORY) return new Resolution(List.of(), true, null);
        List<Long> ids = List.of();
        boolean compare = type == ReferenceType.LAST_COMPARED_PRODUCTS;
        if (type == ReferenceType.RECENT_PRODUCT && !memory.productTurns().isEmpty()) {
            ids = memory.productTurns().get(0).productIds();
        } else if (compare) {
            ids = memory.productTurns().stream().filter(t -> "COMPARE".equals(t.intent()))
                    .findFirst().map(ConversationMemory.Turn::productIds).orElse(List.of());
            if (ids.isEmpty()) {
                ids = memory.topics().stream().filter(t -> t.context().containsKey("lastComparedProductIds"))
                        .max(Comparator.comparingLong(t -> t.context().get("lastComparisonTurnId") instanceof Number n ? n.longValue() : 0))
                        .map(t -> ids(t.context().get("lastComparedProductIds"))).orElse(List.of());
            }
        } else if (topic != null) {
            ids = ids(topic.context().get("lastProductIds"));
            if (ids.isEmpty()) ids = ids(topic.context().get("lastComparedProductIds"));
        }
        if (ids.isEmpty()) {
            ids = memory.productTurns().stream()
                    .filter(t -> !compare || "COMPARE".equals(t.intent()))
                    .findFirst().map(ConversationMemory.Turn::productIds).orElse(List.of());
        }
        if (ids.isEmpty()) return new Resolution(List.of(), true,
                "Em chưa tìm thấy danh sách sản phẩm được tham chiếu trong lịch sử. Anh/chị cho biết tên máy nhé?");
        if (type == ReferenceType.RECENT_PRODUCT && criteria.getProductPositions() == null && ids.size() > 1) {
            return new Resolution(ids, true, "Anh/chị đang nói tới máy nào trong danh sách vừa xem?");
        }
        if (type == ReferenceType.PRODUCT_BY_POSITION || criteria.getProductPositions() != null) {
            if (criteria.getProductPositions() == null || criteria.getProductPositions().isEmpty()) {
                return new Resolution(ids, true, "Anh/chị muốn xem sản phẩm ở vị trí nào?");
            }
            List<Long> selected = new ArrayList<>();
            for (int position : criteria.getProductPositions()) {
                if (position < 1 || position > ids.size()) return new Resolution(ids, true,
                        "Vị trí đó không có trong danh sách đã hiển thị. Anh/chị chọn lại giúp em nhé?");
                if (!selected.contains(ids.get(position - 1))) selected.add(ids.get(position - 1));
            }
            ids = selected;
        }
        if ("COMPARE".equals(criteria.getIntent()) && ids.size() != 2 && criteria.getResultLimit() == null
                && criteria.getRankingCriterion() == null) {
            return new Resolution(ids, true, "Anh/chị muốn so sánh những vị trí nào trong danh sách?");
        }
        return new Resolution(List.copyOf(ids), true, null);
    }

    static List<Long> ids(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream().filter(Number.class::isInstance).map(Number.class::cast).map(Number::longValue)
                .filter(id -> id > 0).distinct().limit(50).toList();
    }
    private static boolean hasNames(GeminiFilterCriteria c) {
        return c.getTargetProductName() != null && !c.getTargetProductName().isBlank()
                || c.getCompareProductNames() != null && !c.getCompareProductNames().isEmpty();
    }
}
