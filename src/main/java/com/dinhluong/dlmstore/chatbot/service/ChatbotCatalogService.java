package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.ChatbotSearchResult;
import com.dinhluong.dlmstore.chatbot.mapper.ChatbotProductMapper;
import com.dinhluong.dlmstore.dto.GeminiFilterCriteria;
import com.dinhluong.dlmstore.entity.Product;
import com.dinhluong.dlmstore.entity.Enums.*;
import com.dinhluong.dlmstore.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.JoinType;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatbotCatalogService {
    private final ProductRepository repository;
    private final ChatbotProductMapper mapper;
    public record NamedResult(ChatbotSearchResult products, String question, Map<String,Object> pendingData) {
        public NamedResult(ChatbotSearchResult products, String question) { this(products, question, Map.of()); }
    }

    /** Only call with IDs obtained from authorized memory or a database name lookup, never raw AI IDs. */
    @Transactional(readOnly = true)
    public ChatbotSearchResult reload(List<Long> orderedIds) {
        if (orderedIds.size() > 50 || orderedIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Invalid product reference set");
        }
        Map<Long, Product> found = new HashMap<>();
        repository.findAllById(orderedIds).stream().filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .forEach(p -> found.put(p.getId(), p));
        return mapped(orderedIds.stream().distinct().map(found::get).filter(Objects::nonNull).toList(), "");
    }

    @Transactional(readOnly = true)
    public NamedResult byNames(List<String> names) {
        List<Product> selected = new ArrayList<>();
        Map<String,Object> ambiguity = new LinkedHashMap<>();
        String question = null;
        for (String name : names) {
            var candidates = repository.findAll((Specification<Product>) (root, query, cb) -> cb.and(
                    cb.equal(root.get("status"), ProductStatus.ACTIVE),
                    cb.like(cb.lower(root.get("name")), "%" + escape(name.toLowerCase(Locale.ROOT)) + "%", '\\')),
                    PageRequest.of(0, 16, Sort.by("id"))).getContent();
            var exact = candidates.stream().filter(p -> TopicResolutionService.normalize(p.getName())
                    .equals(TopicResolutionService.normalize(name))).toList();
            var matches = exact.isEmpty() ? candidates : exact;
            if (matches.size() != 1) {
                if (question != null) continue;
                ambiguity.put("unresolvedName", name);
                ambiguity.put("candidateNames", matches.stream().collect(java.util.stream.Collectors.toMap(
                        p -> p.getId().toString(), Product::getName, (a,b) -> a, LinkedHashMap::new)));
                question = matches.isEmpty()
                    ? "Không tìm thấy tên sản phẩm đang bán: " + name + ". Anh/chị kiểm tra lại tên nhé?"
                    : "Tên " + name + " có nhiều kết quả. Anh/chị chọn tên đầy đủ: "
                            + String.join(", ", matches.stream().limit(6).map(p -> p.getName() + " [ID " + p.getId()
                                    + ", " + p.getDisplayPrice() + "]").toList()) + "?";
                continue;
            }
            if (selected.stream().noneMatch(p -> p.getId().equals(matches.get(0).getId()))) selected.add(matches.get(0));
        }
        ambiguity.put("resolvedProductIds", selected.stream().map(Product::getId).toList());
        return new NamedResult(mapped(selected, ""), question, question == null ? Map.of() : ambiguity);
    }

    @Transactional(readOnly = true)
    public ChatbotSearchResult search(GeminiFilterCriteria c) {
        boolean ranked = c.getRankingCriterion() != null || Set.of("RECOMMEND", "RANK").contains(c.getIntent())
                || Set.of("gaming", "battery", "photography").contains(String.valueOf(c.getUsageGoal()));
        int size = ranked ? 50 : c.getResultLimit() == null ? 5 : c.getResultLimit();
        var page = repository.findAll(specification(c), PageRequest.of(c.getResultPage() == null ? 0 : c.getResultPage(), size,
                Sort.by(Sort.Direction.ASC, "displayPrice").and(Sort.by("id"))));
        var result = mapped(page.getContent(), "");
        return new ChatbotSearchResult(result.aiProducts(), result.uiProducts(), result.fallbackNote(), page.hasNext());
    }

    private Specification<Product> specification(GeminiFilterCriteria c) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
            if (c.getCategorySlug() != null) predicates.add(cb.equal(root.get("category").get("slug"), c.getCategorySlug()));
            if (c.getBrands() != null && !c.getBrands().isEmpty()) predicates.add(root.get("brand").get("name").in(c.getBrands()));
            if (c.getMinPrice() != null) predicates.add(cb.ge(root.get("displayPrice"), c.getMinPrice()));
            if (c.getMaxPrice() != null) predicates.add(cb.le(root.get("displayPrice"), c.getMaxPrice()));
            if (c.getMinBattery() != null) predicates.add(cb.ge(root.get("batteryCapacity"), c.getMinBattery()));
            if (c.getMinScreenSize() != null) predicates.add(cb.ge(root.get("screenSize"), c.getMinScreenSize()));
            if (c.getNetworks() != null && c.getNetworks().contains("5G")) predicates.add(cb.isTrue(root.get("support5g")));
            if (c.getOsTypes() != null && !c.getOsTypes().isEmpty()) predicates.add(root.get("osType")
                    .in(c.getOsTypes().stream().map(s -> OsType.valueOf(s.toUpperCase(Locale.ROOT))).toList()));
            if (c.getSearchKeyword() != null && !c.getSearchKeyword().isBlank()) {
                String pattern = "%" + escape(c.getSearchKeyword().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("searchKeywords")), pattern, '\\')));
            }
            if (c.getRam() != null || c.getRom() != null || c.getColor() != null || Boolean.TRUE.equals(c.getInStockOnly())) {
                var variant = root.join("variants", JoinType.INNER);
                predicates.add(cb.isTrue(variant.get("isActive")));
                if (c.getRam() != null) predicates.add(cb.equal(cb.lower(variant.get("ram")), c.getRam().toLowerCase(Locale.ROOT)));
                if (c.getRom() != null) predicates.add(cb.equal(cb.lower(variant.get("rom")), c.getRom().toLowerCase(Locale.ROOT)));
                if (c.getColor() != null) predicates.add(cb.equal(cb.lower(variant.get("colorName")), c.getColor().toLowerCase(Locale.ROOT)));
                if (Boolean.TRUE.equals(c.getInStockOnly())) predicates.add(cb.gt(variant.get("stockQuantity"), 0));
            }
            for (var requested : Map.of("chip", c.getChip() == null ? "" : c.getChip(),
                    "camera", c.getCamera() == null ? "" : c.getCamera()).entrySet()) {
                if (requested.getValue().isBlank()) continue;
                var spec = root.join("specValues", JoinType.INNER);
                var label = cb.lower(spec.get("attribute").get("name"));
                predicates.add("chip".equals(requested.getKey()) ? cb.or(cb.like(label, "%chip%"), cb.like(label, "%processor%"),
                        cb.like(label, "%cpu%"), cb.like(label, "%vi xử lý%")) : cb.like(label, "%camera%"));
                predicates.add(cb.like(cb.lower(spec.get("value")), "%" + escape(requested.getValue().toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private ChatbotSearchResult mapped(List<Product> products, String note) {
        return new ChatbotSearchResult(products.stream().map(mapper::toAiDto).toList(),
                products.stream().map(mapper::toUiDto).toList(), note);
    }
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
