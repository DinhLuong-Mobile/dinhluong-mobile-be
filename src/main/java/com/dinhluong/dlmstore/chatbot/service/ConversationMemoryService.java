package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.*;
import com.dinhluong.dlmstore.chatbot.enums.ClarificationStatus;
import com.dinhluong.dlmstore.chatbot.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConversationMemoryService {
    private final ConversationSessionService sessions;
    private final ChatbotTopicRepository topics;
    private final ChatbotInteractionRepository history;
    private final ChatbotClarificationRepository clarifications;
    private final com.dinhluong.dlmstore.repository.CategoryRepository categories;
    private final com.dinhluong.dlmstore.repository.BrandRepository brands;
    private final ChatbotCatalogService catalog;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    @Transactional
    public boolean expireIfOwned(String id, ConversationPrincipal principal) {
        return sessions.markExpiredIfOwned(id, principal);
    }

    /** Ownership is checked before any history query, in a short transaction. */
    @Transactional
    public ConversationMemory load(String conversationId, ConversationPrincipal principal) {
        var session = sessions.getOrCreateConversation(conversationId, principal);
        var rows = history.findByConversationIdOrderByIdDesc(session.getId(), PageRequest.of(0, 200));
        var topicRows = topics.findByConversationIdOrderByUpdatedAtDesc(session.getId(), PageRequest.of(0, 100));
        List<ConversationMemory.Topic> snapshots = topicRows.stream().map(t -> new ConversationMemory.Topic(
                t.getId(), t.getTopicName(), copy(t.getContextData()), t.getContextSummary())).toList();
        List<ConversationMemory.Turn> productTurns = new ArrayList<>(rows.stream()
                .filter(r -> r.getPresentedProducts() != null && !r.getPresentedProducts().isEmpty())
                .map(r -> new ConversationMemory.Turn(r.getId(), r.getTopicId(), r.getIntent(), "", "",
                        List.copyOf(r.getPresentedProducts()))).toList());
        // A valid comparison must remain retrievable even after it leaves the recent-turn window.
        var olderComparison = history.findByConversationIdAndIntentAndProcessingStatusOrderByIdDesc(session.getId(), "COMPARE",
                com.dinhluong.dlmstore.chatbot.enums.ChatProcessingStatus.SUCCESS, PageRequest.of(0, 20)).stream()
                .filter(r -> r.getPresentedProducts() != null && !r.getPresentedProducts().isEmpty()).findFirst();
        if (olderComparison.isPresent()) {
            var row = olderComparison.get();
            if (productTurns.stream().noneMatch(t -> t.id().equals(row.getId()))) {
                productTurns.add(new ConversationMemory.Turn(row.getId(), row.getTopicId(), row.getIntent(), "", "", List.copyOf(row.getPresentedProducts())));
                productTurns.sort(Comparator.comparing(ConversationMemory.Turn::id).reversed());
            }
            if (row.getTopicId() != null && snapshots.stream().noneMatch(t -> t.id().equals(row.getTopicId()))) {
                var comparedTopic = topics.findByIdAndConversationId(row.getTopicId(), session.getId());
                if (comparedTopic.isPresent()) {
                    var t = comparedTopic.get(); snapshots = new ArrayList<>(snapshots);
                    snapshots.add(new ConversationMemory.Topic(t.getId(), t.getTopicName(), copy(t.getContextData()), t.getContextSummary()));
                }
            }
        }
        List<ConversationMemory.Turn> recent = new ArrayList<>();
        int remaining = 6000;
        for (var row : rows) {
            if (recent.size() >= 12 || remaining <= 0) break;
            String query = truncate(row.getQuery(), Math.min(400, remaining)); remaining -= query.length();
            String answer = truncate(row.getResponse(), Math.min(400, remaining)); remaining -= answer.length();
            recent.add(new ConversationMemory.Turn(row.getId(), row.getTopicId(), row.getIntent(), query, answer,
                    row.getPresentedProducts() == null ? List.of() : List.copyOf(row.getPresentedProducts())));
        }
        ConversationMemory.Pending pending = null;
        if (session.getActiveTopicId() != null) {
            var found = clarifications.findByConversationIdAndTopicIdAndStatus(session.getId(),
                    session.getActiveTopicId(), ClarificationStatus.PENDING);
            if (found.isPresent()) {
                var p = found.get();
                if (p.getExpiresAt() != null && !p.getExpiresAt().isAfter(LocalDateTime.now())) {
                    p.setStatus(ClarificationStatus.EXPIRED);
                } else {
                    repairLegacyPending(p);
                    pending = new ConversationMemory.Pending(p.getId(), p.getTopicId(), p.getQuestion(),
                            p.getOriginalMessage(), copy(p.getCandidateData()), p.getAttempts(), p.getMaxAttempts());
                }
            }
        }
        return new ConversationMemory(session.getId(), session.getActiveTopicId(),
                rows.isEmpty() ? null : rows.get(0).getId(), snapshots, List.copyOf(recent), productTurns, pending);
    }

    /** Upgrade old pending JSON under the authorized session lock, before accepting any selection. */
    private void repairLegacyPending(com.dinhluong.dlmstore.chatbot.entity.ChatbotClarification pending) {
        var data = new LinkedHashMap<>(copy(pending.getCandidateData()));
        if (!data.containsKey("unresolvedSlot") && TopicResolutionService.normalize(pending.getQuestion()).contains("ngan sach"))
            data.put("unresolvedSlot", "budget");
        if (!data.containsKey("candidateNames") && data.get("criteria") instanceof Map<?,?>) {
            var criteria = mapper.convertValue(data.get("criteria"), com.dinhluong.dlmstore.dto.GeminiFilterCriteria.class);
            var names = new ArrayList<String>();
            if (criteria.getCompareProductNames() != null) names.addAll(criteria.getCompareProductNames());
            if (criteria.getTargetProductName() != null) names.add(criteria.getTargetProductName());
            if (!names.isEmpty()) {
                var named = catalog.byNames(names);
                if (named.question() != null && named.pendingData().get("candidateNames") instanceof Map<?,?> candidates
                        && !candidates.isEmpty()) {
                    data.putAll(named.pendingData()); data.put("unresolvedSlot", "product");
                    data.put("candidateIds", candidates.keySet().stream().map(id -> Long.valueOf(id.toString())).toList());
                    pending.setQuestion(named.question());
                }
            }
        }
        if (!data.equals(pending.getCandidateData())) pending.setCandidateData(data);
    }

    public IntentConversationContext forIntent(ConversationMemory memory) {
        var active = memory.activeTopic();
        var compared = memory.productTurns().stream().filter(t -> "COMPARE".equals(t.intent()))
                .findFirst().map(ConversationMemory.Turn::productIds).orElse(List.of());
        Map<String, String> categoryNames = new LinkedHashMap<>();
        categories.findAll(PageRequest.of(0, 40)).forEach(c -> categoryNames.put(c.getSlug(), c.getName()));
        List<String> brandNames = brands.findAll(PageRequest.of(0, 40)).stream().map(b -> b.getName()).toList();
        return new IntentConversationContext(memory.activeTopicId(), active == null ? Map.of() : active.context(),
                memory.topics().stream().limit(20).map(t -> new IntentConversationContext.TopicSummary(t.id(), t.name(),
                        truncate(t.summary(), 300))).toList(),
                memory.pending() == null ? null : memory.pending().question(), null, memory.recentTurns(), compared, categoryNames, brandNames);
    }

    @Transactional
    public ConversationMemory withRelevantTopics(ConversationMemory snapshot,
            com.dinhluong.dlmstore.dto.GeminiFilterCriteria criteria, ConversationPrincipal principal) {
        if (criteria.getTopicDescription() == null || criteria.getTopicDescription().isBlank()) return snapshot;
        sessions.getActiveConversation(snapshot.conversationId(), principal);
        Map<Long, ConversationMemory.Topic> all = new LinkedHashMap<>();
        snapshot.topics().forEach(t -> all.put(t.id(), t));
        topics.findRelated(snapshot.conversationId(), criteria.getTopicDescription(), PageRequest.of(0, 20))
                .forEach(t -> all.put(t.getId(), new ConversationMemory.Topic(t.getId(), t.getTopicName(), copy(t.getContextData()), t.getContextSummary())));
        return new ConversationMemory(snapshot.conversationId(), snapshot.activeTopicId(), snapshot.revision(),
                List.copyOf(all.values()), snapshot.recentTurns(), snapshot.productTurns(), snapshot.pending());
    }

    private static String truncate(String value, int size) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), Math.max(0, size)));
    }
    private static Map<String, Object> copy(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }
}
