package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.*;
import com.dinhluong.dlmstore.chatbot.enums.*;
import com.dinhluong.dlmstore.chatbot.exception.ConversationStateException;
import com.dinhluong.dlmstore.chatbot.repository.*;
import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConversationTurnStore {
    private final ConversationSessionService sessions;
    private final ConversationTopicService topicService;
    private final ChatbotTopicRepository topicRepository;
    private final ChatbotInteractionRepository historyRepository;
    private final ClarificationService clarifications;
    private final ChatHistoryService history;
    private final ObjectMapper mapper;
    private final ChatbotClarificationRepository clarificationRepository;
    private final com.dinhluong.dlmstore.repository.ProductRepository products;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /** No network call is permitted here. Lock, check snapshot revision, and commit one complete turn. */
    @Transactional
    public ChatBotResponse commit(ConversationPrincipal principal, String message, AiModel model, PreparedTurn plan) {
        var memory = plan.memory();
        var session = sessions.getActiveConversation(memory.conversationId(), principal);
        // OpenEntityManagerInView may retain an entity from the snapshot transaction.
        entityManager.refresh(session);
        sessions.getActiveConversation(memory.conversationId(), principal);
        var latest = historyRepository.findByConversationIdOrderByIdDesc(session.getId(), PageRequest.of(0, 1));
        Long revision = latest.isEmpty() ? null : latest.get(0).getId();
        if (!Objects.equals(revision, memory.revision()) || !Objects.equals(session.getActiveTopicId(), memory.activeTopicId())) {
            throw new ConversationStateException("Hội thoại đã thay đổi khi xử lý; vui lòng gửi lại tin nhắn.");
        }
        var topic = plan.newTopic() || plan.selectedTopicId() == null
                ? topicService.createTopic(session.getId(), principal, topicName(plan))
                : topicService.getTopic(session.getId(), principal, plan.selectedTopicId());
        if (!plan.newTopic() && plan.selectedTopicId() != null) entityManager.refresh(topic);
        var original = memory.topics().stream().filter(t -> t.id().equals(topic.getId())).findFirst();
        if (original.isPresent() && !sameContext(original.get().context(),
                topic.getContextData() == null ? Map.of() : topic.getContextData())) {
            throw new ConversationStateException("Ngữ cảnh đã được cập nhật bởi request khác; vui lòng thử lại.");
        }
        session.setActiveTopicId(topic.getId());
        var response = plan.response();
        validateResponse(response);
        validateCurrentProducts(response, plan);
        var c = plan.criteria();
        if (memory.pending() != null && (c.getMessageAction() == MessageAction.NEW_REQUEST
                || c.getMessageAction() == MessageAction.CANCEL)) {
            var previous = clarifications.getPending(session.getId(), principal, memory.pending().topicId());
            if (previous.isPresent()) clarifications.cancel(session.getId(), principal,
                    memory.pending().topicId(), previous.get().getId());
        }
        var pending = clarifications.getPending(session.getId(), principal, topic.getId());
        if (c.getMessageAction() == MessageAction.ANSWER_CLARIFICATION && memory.pending() != null
                && (pending.isEmpty() || !pending.get().getId().equals(memory.pending().id())))
            throw new ConversationStateException("Pending clarification is no longer available");
        if (pending.isPresent() && (c.getMessageAction() == MessageAction.CANCEL || c.getMessageAction() == MessageAction.NEW_REQUEST)) {
            clarifications.cancel(session.getId(), principal, topic.getId(), pending.get().getId());
            pending = Optional.empty();
        }
        if (plan.clarificationQuestion() != null) {
            if (pending.isPresent() && c.getMessageAction() == MessageAction.ANSWER_CLARIFICATION
                    && !PendingAnswerParser.budget(message).isEmpty()
                    && "budget".equals(pending.get().getCandidateData().get("unresolvedSlot"))
                    && !plan.clarificationQuestion().equals(pending.get().getQuestion())) {
                clarifications.resolve(session.getId(), principal, topic.getId(), pending.get().getId(),
                        Map.of("message", message, "criteria", mapper.convertValue(c, new TypeReference<Map<String,Object>>() { })));
                pending = Optional.empty();
            }
            if (pending.isPresent()) {
                var updated = clarifications.recordFailedAttempt(session.getId(), principal, topic.getId(), pending.get().getId());
                if (updated.isPending()) {
                    response.setClarificationId(updated.getId()); response.setRequiresClarification(true);
                    response.setAnswer(updated.getQuestion());
                } else response.setAnswer("Đã đạt giới hạn làm rõ. Anh/chị bắt đầu yêu cầu mới giúp em nhé.");
            } else {
                var latestQuestion = clarificationRepository.findTopByConversationIdAndTopicIdOrderByIdDesc(session.getId(), topic.getId());
                boolean exhausted = latestQuestion.isPresent() && latestQuestion.get().getStatus() == ClarificationStatus.CANCELLED
                        && latestQuestion.get().getAttempts() >= latestQuestion.get().getMaxAttempts()
                        && c.getMessageAction() != MessageAction.NEW_REQUEST;
                if (exhausted) {
                    response.setAnswer("Đã đạt giới hạn làm rõ. Anh/chị gửi một yêu cầu mới với thông tin cụ thể giúp em nhé.");
                } else {
                Map<String, Object> criteria = mapper.convertValue(c, new TypeReference<>() { });
                Map<String,Object> pendingData = new LinkedHashMap<>();
                pendingData.put("criteria", criteria);
                pendingData.put("candidateIds", plan.candidateIds());
                if (plan.context().get("_clarification") instanceof Map<?,?> metadata)
                    metadata.forEach((key,value) -> pendingData.put(key.toString(),value));
                pendingData.put("originalIntent", c.getIntent());
                pendingData.put("unresolvedSlot", pendingData.containsKey("candidateNames") ? "product"
                        : TopicResolutionService.normalize(plan.clarificationQuestion()).contains("ngan sach") ? "budget" : "criteria");
                var question = clarifications.create(session.getId(), principal, topic.getId(), new CreateClarificationRequest(
                        ClarificationType.AMBIGUOUS_REFERENCE, pendingData.get("unresolvedSlot").toString(), message, plan.clarificationQuestion(),
                        pendingData, LocalDateTime.now().plusMinutes(30)));
                response.setClarificationId(question.getId()); response.setRequiresClarification(true);
                }
            }
        } else if (pending.isPresent() && c.getMessageAction() == MessageAction.ANSWER_CLARIFICATION) {
            if (memory.pending() == null || !memory.pending().id().equals(pending.get().getId()))
                throw new ConversationStateException("Pending clarification changed during processing");
            Map<String,Object> resolved = new LinkedHashMap<>();
            resolved.put("message", message);
            resolved.put("originalIntent", c.getIntent());
            resolved.put("productIds", response.getProducts().stream().map(p -> p.getId()).toList());
            resolved.put("criteria", mapper.convertValue(c, new TypeReference<Map<String,Object>>() { }));
            clarifications.resolve(session.getId(), principal, topic.getId(), pending.get().getId(), resolved);
        }
        Map<String, Object> context = new LinkedHashMap<>(plan.context());
        context.remove("_clarification");
        List<Long> presented = response.getProducts().stream().map(p -> p.getId()).toList();
        if (!response.isRequiresClarification()) {
            if (Set.of("SEARCH", "RECOMMEND", "RANK").contains(c.getIntent())) context.put("lastProductIds", presented);
            if (Set.of("SEARCH", "RECOMMEND").contains(c.getIntent())) context.put("lastSearchPage", c.getResultPage() == null ? 0 : c.getResultPage());
            if ("COMPARE".equals(c.getIntent()) && !presented.isEmpty()) {
                context.put("lastComparedProductIds", presented);
                if (c.getComparisonVariants() == null && c.getReferenceType() != ReferenceType.LAST_COMPARED_PRODUCTS) {
                    context.remove("lastComparedVariantRefs");
                }
            }
        }
        topic.setContextData(context);
        if (Set.of("SEARCH", "RECOMMEND", "RANK", "COMPARE", "DETAIL", "EXPLAIN").contains(c.getIntent())) {
            String summary = topic.getTopicName() + "; bộ lọc: " + context.entrySet().stream()
                    .filter(e -> FilterMergeService.FIELDS.contains(e.getKey())).toList()
                    + "; đã hiển thị: " + String.join(", ", response.getProducts().stream().map(p -> p.getName()).toList());
            topic.setContextSummary(summary.substring(0, Math.min(2000, summary.length())));
        }
        response.setConversationId(session.getId()); response.setTopicId(topic.getId());
        sessions.updateActivity(session.getId(), principal);
        var written = history.save(principal.userId(), session.getId(), topic.getId(), message, response, c.getIntent(),
                c.getContextAction() == null ? "CONTINUE" : c.getContextAction().name(),
                c.getMessageAction() == null ? "NONE" : c.getMessageAction().name(), model);
        if (plan.clarificationQuestion() != null && !response.isRequiresClarification()) {
            written.setProcessingStatus(ChatProcessingStatus.FAILED);
        }
        if (!response.isRequiresClarification()) {
            if ("COMPARE".equals(c.getIntent()) && !presented.isEmpty()) context.put("lastComparisonTurnId", written.getId());
            if (Set.of("SEARCH", "RECOMMEND", "RANK").contains(c.getIntent())) context.put("lastPresentationTurnId", written.getId());
            topic.setContextData(new LinkedHashMap<>(context));
        }
        return response;
    }

    private static String topicName(PreparedTurn plan) {
        String name = plan.criteria().getTopicName();
        if (name == null || name.isBlank()) {
            name = "Tư vấn " + (plan.criteria().getUsageGoal() == null ? "sản phẩm" : plan.criteria().getUsageGoal());
            if (plan.criteria().getBrands() != null) name += " " + String.join(", ", plan.criteria().getBrands());
        }
        return name.substring(0, Math.min(150, name.length()));
    }

    private boolean sameContext(Map<String, Object> left, Map<String, Object> right) {
        com.fasterxml.jackson.databind.JsonNode a = mapper.valueToTree(left), b = mapper.valueToTree(right);
        // JSON round-trips may turn a Long into Integer, or BigDecimal into Double.
        return a.equals((x, y) -> x.isNumber() && y.isNumber()
                ? x.decimalValue().compareTo(y.decimalValue()) : x.equals(y) ? 0 : 1, b);
    }

    private void validateCurrentProducts(ChatBotResponse response, PreparedTurn plan) {
        var ids = response.getProducts().stream().map(p -> p.getId()).toList();
        Map<Long, com.dinhluong.dlmstore.entity.Product> current = new HashMap<>();
        products.findAllById(ids).forEach(p -> { entityManager.refresh(p); current.put(p.getId(), p); });
        for (var card : response.getProducts()) {
            var p = current.get(card.getId());
            if (p == null || p.getStatus() != com.dinhluong.dlmstore.entity.Enums.ProductStatus.ACTIVE
                    || (p.getDisplayPrice() == null) != (card.getPrice() == null)
                    || p.getDisplayPrice() != null && p.getDisplayPrice().compareTo(card.getPrice()) != 0) {
                throw new ConversationStateException("Giá hoặc trạng thái sản phẩm vừa thay đổi; vui lòng thử lại để nhận dữ liệu mới.");
            }
        }
        if (plan.variantResponse() && plan.context().get("lastComparedVariantRefs") instanceof List<?> refs) {
            for (Object item : refs) {
                var ref = (Map<?, ?>)item;
                var p = current.get(((Number)ref.get("productId")).longValue());
                long variantId = ((Number)ref.get("variantId")).longValue();
                var variant = p == null || p.getVariants() == null ? null : p.getVariants().stream()
                        .filter(v -> v.isActive() && v.getId() == variantId).findFirst().orElse(null);
                Integer version = ref.get("version") instanceof Number n ? n.intValue() : null;
                if (variant == null || !Objects.equals(version, variant.getVersion())) {
                    throw new ConversationStateException("Biến thể vừa thay đổi; vui lòng thử lại để lấy giá và tồn kho mới.");
                }
            }
        }
    }

    private static void validateResponse(ChatBotResponse response) {
        if (response == null || response.getAnswer() == null || response.getAnswer().isBlank() || response.getProducts() == null) {
            throw new IllegalStateException("Incomplete chatbot response");
        }
        Set<Long> ids = new HashSet<>();
        for (var p : response.getProducts()) if (p == null || p.getId() == null || p.getId() <= 0 || !ids.add(p.getId())) {
            throw new IllegalStateException("Invalid or duplicate response product");
        }
    }
}
