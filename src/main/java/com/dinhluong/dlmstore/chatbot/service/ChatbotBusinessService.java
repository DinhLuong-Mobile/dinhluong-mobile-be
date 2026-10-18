package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.*;
import com.dinhluong.dlmstore.chatbot.enums.*;
import com.dinhluong.dlmstore.dto.GeminiFilterCriteria;
import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatbotBusinessService {
    private final ChatbotCatalogService catalog;
    private final ChatbotProductSearchService alternatives;
    private final ProductReferenceResolver references;
    private final TopicResolutionService topics;
    private final FilterMergeService filters;
    private final ProductRankingService ranking;
    private final GroundedProductResponseService grounded;
    private final ChatbotAnswerService legacyAnswers;
    private final ObjectMapper mapper;
    private final ProductVariantComparisonService variants;

    public PreparedTurn prepare(ConversationMemory memory, GeminiFilterCriteria extracted, String message, AiModel model) {
        var deterministic = extracted.getPendingSelectedIds() == null ? pendingAnswer(memory, message) : extracted;
        if (deterministic != null) extracted = deterministic;
        else if (memory.pending() != null && extracted.getMessageAction() != MessageAction.NEW_REQUEST
                && extracted.getMessageAction() != MessageAction.CANCEL
                && extracted.getMessageAction() != MessageAction.ANSWER_CLARIFICATION
                && (memory.pending().candidateData().containsKey("candidateNames")
                    || "budget".equals(memory.pending().candidateData().get("unresolvedSlot")))
                && extracted.getContextAction() != ContextAction.SWITCH_TOPIC
                && !"RECALL".equals(extracted.getIntent())
                && (extracted.getProductPositions() == null || extracted.getProductPositions().isEmpty())) {
            // Unrelated or invalid selections consume the existing question, never spawn a parallel topic.
            extracted.setMessageAction(MessageAction.ANSWER_CLARIFICATION);
            extracted.setRequiresClarification(true);
            extracted.setClarificationQuestion(memory.pending().question());
        }
        GeminiFilterCriteria criteria = resumePending(extracted, memory);
        if (memory.pending() != null && criteria.getMessageAction() == MessageAction.CANCEL) {
            criteria.setContextAction(ContextAction.CONTINUE);
            return turn(memory, criteria, memory.pending().topicId(), false,
                    new LinkedHashMap<>(memory.activeTopic().context()), new ChatBotResponse("Đã hủy yêu cầu làm rõ.", List.of()));
        }
        var topic = topics.resolve(criteria, memory);
        Map<String, Object> prior = topic.topic() == null || topic.create() ? Map.of() : topic.topic().context();
        boolean productAction = !Set.of("CHAT", "OUT_OF_DOMAIN", "RECALL").contains(criteria.getIntent());
        Map<String, Object> context = productAction ? filters.merge(prior, criteria, topic.create()) : new LinkedHashMap<>(prior);
        Long topicId = topic.topic() == null ? null : topic.topic().id();
        if (topic.question() != null) return clarification(memory, criteria, topicId, false, context, topic.question(), List.of());
        if (Boolean.TRUE.equals(criteria.getRequiresClarification())) return clarification(memory, criteria, topicId,
                topic.create(), context, criteria.getClarificationQuestion(), List.of());
        if (!productAction && !"RECALL".equals(criteria.getIntent())) {
            return turn(memory, criteria, topicId, false, context, legacyAnswers.generate(message, criteria, model));
        }
        if (criteria.getComparisonVariants() != null && !criteria.getComparisonVariants().isEmpty()) {
            var compared = variants.compare(criteria.getComparisonVariants());
            if (compared.question() != null) return clarification(memory, criteria, topicId, topic.create(), context,
                    compared.question(), List.of());
            context.put("lastComparedVariantRefs", compared.references());
            return new PreparedTurn(memory,criteria,topicId,topic.create(),context,compared.response(),null,List.of(),true);
        }
        Object comparedVariants = memory.productTurns().stream().filter(t -> "COMPARE".equals(t.intent())).findFirst()
                .flatMap(t -> memory.topics().stream().filter(candidate -> candidate.id().equals(t.topicId())).findFirst())
                .map(t -> t.context().get("lastComparedVariantRefs")).orElse(null);
        if (criteria.getReferenceType() == ReferenceType.LAST_COMPARED_PRODUCTS && comparedVariants instanceof List<?>) {
            List<Map<String, Object>> refs = mapper.convertValue(comparedVariants,
                    new com.fasterxml.jackson.core.type.TypeReference<>() { });
            var compared = variants.reload(refs);
            if (compared.question() != null) return clarification(memory, criteria, topicId, false, context, compared.question(), List.of());
            context.put("lastComparedVariantRefs", compared.references());
            return new PreparedTurn(memory,criteria,topicId,false,context,compared.response(),null,List.of(),true);
        }
        var referenceTopic = topic.topic();
        if (criteria.getMessageAction() == MessageAction.ANSWER_CLARIFICATION && memory.pending() != null) {
            var boundIds = ProductReferenceResolver.ids(memory.pending().candidateData().get("candidateIds"));
            if (!boundIds.isEmpty() && criteria.getTargetProductName() == null
                    && (criteria.getCompareProductNames() == null || criteria.getCompareProductNames().isEmpty())) {
                referenceTopic = new ConversationMemory.Topic(topicId, "", Map.of("lastProductIds", boundIds), "");
                criteria.setReferenceType(criteria.getProductPositions() == null
                        ? ReferenceType.LAST_PRESENTED_LIST : ReferenceType.PRODUCT_BY_POSITION);
            }
        }
        boolean freshBudgetResume = memory.pending() != null && criteria.getMessageAction() == MessageAction.ANSWER_CLARIFICATION
                && "budget".equals(memory.pending().candidateData().get("unresolvedSlot"))
                && ProductReferenceResolver.ids(memory.pending().candidateData().get("candidateIds")).isEmpty()
                && (criteria.getReferenceType() == null || criteria.getReferenceType() == ReferenceType.NONE);
        var ref = freshBudgetResume ? new ProductReferenceResolver.Resolution(List.of(),false,null)
                : references.resolve(criteria, memory, referenceTopic);
        if (ref.question() != null) return clarification(memory, criteria, topicId, topic.create(), context, ref.question(), ref.ids());
        if ("RECOMMEND".equals(criteria.getIntent()) && criteria.getRankingCriterion() == null && context.get("usageGoal") == null) {
            return clarification(memory, criteria, topicId, topic.create(), context,
                    "Anh/chị muốn ưu tiên chơi game, pin, camera hay giá và dự kiến ngân sách bao nhiêu?", List.of());
        }
        ChatbotSearchResult found;
        var selectedPendingIds = criteria.getPendingSelectedIds() == null ? selectedPendingIds(memory, message) : criteria.getPendingSelectedIds();
        if (criteria.getMessageAction() == MessageAction.ANSWER_CLARIFICATION && !selectedPendingIds.isEmpty()) {
            found = catalog.reload(selectedPendingIds);
            if (found.aiProducts().size() != selectedPendingIds.size()) return clarification(memory, criteria, topicId,
                    false, context, memory.pending().question(), List.of());
        } else if ("RECALL".equals(criteria.getIntent()) && criteria.getReferenceType() == ReferenceType.CONVERSATION_HISTORY) {
            var ids = memory.productTurns().stream().flatMap(t -> t.productIds().stream()).distinct().limit(50).toList();
            found = catalog.reload(ids);
            String summary = String.join("\n", memory.topics().stream()
                    .map(t -> "- " + t.name() + ": " + (t.summary() == null ? "" : t.summary())).toList());
            var response = ids.isEmpty() ? new ChatBotResponse("Chưa có sản phẩm nào đã được tư vấn trong lịch sử này.\n" + summary, List.of())
                    : grounded.render("RECALL", found, "Các chủ đề đã trao đổi:\n" + summary);
            return turn(memory, criteria, topicId, false, context, response);
        } else if (ref.referenced()) {
            found = catalog.reload(ref.ids());
            if (found.aiProducts().isEmpty()) return clarification(memory, criteria, topicId, topic.create(), context,
                    "Những sản phẩm được tham chiếu không còn được bán hoặc đã bị xóa. Anh/chị muốn tìm danh sách mới không?", ref.ids());
        } else if ("COMPARE".equals(criteria.getIntent()) || "DETAIL".equals(criteria.getIntent())
                || "EXPLAIN".equals(criteria.getIntent())) {
            var names = criteria.getCompareProductNames() == null ? new ArrayList<String>()
                    : new ArrayList<>(criteria.getCompareProductNames());
            if (criteria.getTargetProductName() != null) names.add(criteria.getTargetProductName());
            if (names.isEmpty()) return clarification(memory, criteria, topicId, topic.create(), context,
                    "Anh/chị cho biết tên sản phẩm muốn xem nhé?", List.of());
            var named = catalog.byNames(names);
            if (named.question() != null) {
                context.put("_clarification", named.pendingData());
                var candidateNames = (Map<?,?>) named.pendingData().getOrDefault("candidateNames", Map.of());
                return clarification(memory, criteria, topicId, topic.create(), context, named.question(),
                        candidateNames.keySet().stream().map(key -> Long.valueOf(key.toString())).toList());
            }
            found = named.products();
        } else {
            var effective = filters.effective(context, criteria);
            found = catalog.search(effective);
            if (found.aiProducts().isEmpty()) {
                // Reuse the existing fallback search only for a fresh search, never for a reference.
                var relaxed = alternatives.search(effective);
                if (!relaxed.aiProducts().isEmpty()) found = new ChatbotSearchResult(relaxed.aiProducts(), relaxed.uiProducts(), "alternatives");
            }
        }
        int limit = criteria.getResultLimit() == null ? ("COMPARE".equals(criteria.getIntent()) ? 2 : 5) : criteria.getResultLimit();
        if (criteria.getReferenceType() == ReferenceType.PRODUCT_BY_BRAND) {
            if (criteria.getBrands() == null || criteria.getBrands().isEmpty()) return clarification(memory, criteria, topicId,
                    topic.create(), context, "Anh/chị muốn chọn hãng nào trong danh sách?", ref.ids());
            var brandIds = found.aiProducts().stream().filter(p -> p.getSpecs() != null
                    && criteria.getBrands().stream().anyMatch(b -> b.equalsIgnoreCase(p.getSpecs().get("brand"))))
                    .map(p -> p.getId()).toList();
            found = ordered(found, brandIds);
            if ("DETAIL".equals(criteria.getIntent()) && brandIds.size() > 1 && criteria.getResultLimit() == null) {
                return clarification(memory, criteria, topicId, topic.create(), context,
                        "Có nhiều máy cùng hãng trong danh sách. Anh/chị chọn vị trí hoặc tên máy cụ thể nhé?", brandIds);
            }
        }
        String basis = "";
        boolean goalRanking = Set.of("gaming", "battery", "photography").contains(String.valueOf(context.get("usageGoal")));
        boolean rank = criteria.getRankingCriterion() != null || Set.of("RECOMMEND", "RANK").contains(criteria.getIntent())
                || "SEARCH".equals(criteria.getIntent()) && goalRanking
                || "COMPARE".equals(criteria.getIntent()) && found.aiProducts().size() > 2;
        if (rank && !found.aiProducts().isEmpty()) {
            if (found.truncated() && context.get("maxPrice") != null) return clarification(memory, criteria, topicId,
                    topic.create(), context, "Đã ghi nhận ngân sách. Vẫn có hơn 50 sản phẩm; anh/chị chọn thêm hãng hoặc cấu hình RAM/ROM nhé?", List.of());
            if (found.truncated()) return clarification(memory, criteria, topicId, topic.create(), context,
                    "Có hơn 50 ứng viên phù hợp; anh/chị thêm ngân sách hoặc bộ lọc để xếp hạng có căn cứ trong một tập đủ nhỏ nhé?",
                    List.of());
            RankingCriterion criterion = criteria.getRankingCriterion();
            if (criterion == null) criterion = switch (String.valueOf(context.get("usageGoal"))) {
                case "gaming" -> RankingCriterion.GAMING;
                case "battery" -> RankingCriterion.BATTERY;
                case "photography" -> RankingCriterion.CAMERA;
                default -> RankingCriterion.BALANCED;
            };
            var ranked = ranking.rank(found.aiProducts(), criterion, limit);
            if (ranked.question() != null) return clarification(memory, criteria, topicId, topic.create(), context,
                    ranked.question(), found.aiProducts().stream().map(p -> p.getId()).toList());
            basis = ranked.basis();
            found = ordered(found, ranked.ids());
        } else {
            if ("COMPARE".equals(criteria.getIntent()) && found.aiProducts().size() < 2) {
                return clarification(memory, criteria, topicId, topic.create(), context,
                        "Cần hai sản phẩm khác nhau để so sánh. Anh/chị chọn thêm một máy nhé?",
                        found.aiProducts().stream().map(p -> p.getId()).toList());
            }
            found = ordered(found, found.aiProducts().stream().limit("DETAIL".equals(criteria.getIntent()) ? 1 : limit)
                    .map(p -> p.getId()).toList());
        }
        return turn(memory, criteria, topicId, topic.create(), context, grounded.render(criteria.getIntent(), found, basis));
    }

    private GeminiFilterCriteria resumePending(GeminiFilterCriteria current, ConversationMemory memory) {
        if (memory.pending() == null || current.getMessageAction() != MessageAction.ANSWER_CLARIFICATION
                || !memory.pending().candidateData().containsKey("criteria")) return current;
        var original = mapper.convertValue(memory.pending().candidateData().get("criteria"), GeminiFilterCriteria.class);
        Map<String, Object> originalFields = mapper.convertValue(original, new com.fasterxml.jackson.core.type.TypeReference<>() { });
        Map<String, Object> replyFields = mapper.convertValue(current, new com.fasterxml.jackson.core.type.TypeReference<>() { });
        replyFields.forEach((key, value) -> {
            if (FilterMergeService.FIELDS.contains(key) && value != null
                    && !(value instanceof List<?> list && list.isEmpty())) originalFields.put(key, value);
        });
        original = mapper.convertValue(originalFields, GeminiFilterCriteria.class);
        if (current.getClearFields() != null && !current.getClearFields().isEmpty()) original.setClearFields(current.getClearFields());
        // Restore the original action; a short answer such as "first and third" is not a fresh CHAT/SEARCH.
        if (current.getProductPositions() != null) original.setProductPositions(current.getProductPositions());
        if (current.getTargetProductName() != null) original.setTargetProductName(current.getTargetProductName());
        if (current.getCompareProductNames() != null && !current.getCompareProductNames().isEmpty()) original.setCompareProductNames(current.getCompareProductNames());
        if (current.getRankingCriterion() != null) original.setRankingCriterion(current.getRankingCriterion());
        if (current.getResultLimit() != null) original.setResultLimit(current.getResultLimit());
        if (current.getComparisonVariants() != null) original.setComparisonVariants(current.getComparisonVariants());
        original.setMessageAction(MessageAction.ANSWER_CLARIFICATION);
        original.setContextAction(ContextAction.CONTINUE);
        original.setPendingSelectedIds(current.getPendingSelectedIds());
        original.setRequiresClarification(current.getRequiresClarification());
        original.setClarificationQuestion(current.getClarificationQuestion());
        return original;
    }

    /** Executed after authorization, before inference: common answers do not depend on AI availability. */
    public GeminiFilterCriteria pendingAnswer(ConversationMemory memory, String message) {
        if (memory.pending() == null || !memory.pending().candidateData().containsKey("criteria")) return null;
        var original = mapper.convertValue(memory.pending().candidateData().get("criteria"), GeminiFilterCriteria.class);
        var control = PendingAnswerParser.classify(message, false);
        if (control == PendingAnswerParser.Classification.NEW_REQUEST) return null;
        if (control == PendingAnswerParser.Classification.CANCEL) {
            original.setIntent("CHAT"); original.setMessageAction(MessageAction.CANCEL); original.setRequiresClarification(false);
            return original;
        }
        var budget = PendingAnswerParser.budget(message);
        boolean budgetQuestion = TopicResolutionService.normalize(memory.pending().question()).contains("ngan sach")
                || "budget".equals(memory.pending().candidateData().get("unresolvedSlot"));
        if (budgetQuestion && !budget.isEmpty()) {
            original.setMinPrice(budget.getOrDefault("minPrice", original.getMinPrice()));
            original.setMaxPrice(budget.get("maxPrice"));
        } else {
            var ids = selectedPendingIds(memory, message);
            if (ids.isEmpty()) return null;
            original.setPendingSelectedIds(ids);
        }
        original.setMessageAction(MessageAction.ANSWER_CLARIFICATION);
        original.setContextAction(ContextAction.CONTINUE);
        original.setRequiresClarification(false);
        original.setClarificationQuestion(null);
        return original;
    }

    private List<Long> selectedPendingIds(ConversationMemory memory, String message) {
        if (memory.pending() == null) return List.of();
        var data = memory.pending().candidateData();
        if (!(data.get("candidateNames") instanceof Map<?,?> raw)) return List.of();
        Map<Long,String> names = new LinkedHashMap<>();
        raw.forEach((id,name) -> names.put(Long.valueOf(id.toString()), name.toString()));
        Long selected = PendingAnswerParser.select(message, names);
        var idMatch = java.util.regex.Pattern.compile("(?i)^\\s*(?:id|#)\\s*(\\d+)\\s*$").matcher(message);
        if (idMatch.matches()) {
            try { selected = Long.valueOf(idMatch.group(1)); } catch (NumberFormatException ignored) { return List.of(); }
            if (!names.containsKey(selected)) return List.of();
        }
        if (selected == null) return List.of();
        var result = new ArrayList<>(ProductReferenceResolver.ids(data.get("resolvedProductIds")));
        if (!result.contains(selected)) result.add(selected);
        return result;
    }

    private static ChatbotSearchResult ordered(ChatbotSearchResult result, List<Long> ids) {
        return new ChatbotSearchResult(ids.stream().flatMap(id -> result.aiProducts().stream().filter(p -> p.getId().equals(id))).toList(),
                ids.stream().flatMap(id -> result.uiProducts().stream().filter(p -> p.getId().equals(id))).toList(), result.fallbackNote(), result.truncated());
    }
    private static PreparedTurn turn(ConversationMemory memory, GeminiFilterCriteria c, Long topicId, boolean create,
                                     Map<String, Object> context, ChatBotResponse response) {
        return new PreparedTurn(memory, c, topicId, create, context, response, null, List.of());
    }
    private static PreparedTurn clarification(ConversationMemory memory, GeminiFilterCriteria c, Long topicId,
                                             boolean create, Map<String, Object> context, String question, List<Long> ids) {
        return new PreparedTurn(memory, c, topicId, create, context, new ChatBotResponse(question, List.of()), question, ids);
    }
}
