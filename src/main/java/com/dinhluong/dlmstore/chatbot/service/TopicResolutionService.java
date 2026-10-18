package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.ConversationMemory;
import com.dinhluong.dlmstore.chatbot.enums.*;
import com.dinhluong.dlmstore.dto.GeminiFilterCriteria;
import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.*;

@Service
public class TopicResolutionService {
    public record Selection(ConversationMemory.Topic topic, boolean create, String question) { }

    public Selection resolve(GeminiFilterCriteria criteria, ConversationMemory memory) {
        if (memory.pending() != null && criteria.getMessageAction() == MessageAction.ANSWER_CLARIFICATION) {
            return new Selection(memory.topics().stream().filter(t -> t.id().equals(memory.pending().topicId()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Pending topic missing from authorized memory")), false, null);
        }
        // Meta conversation never creates a topic or changes product context merely because the AI says NEW_TOPIC.
        if (Set.of("CHAT", "OUT_OF_DOMAIN").contains(criteria.getIntent())) {
            return new Selection(memory.activeTopic(), false, null);
        }
        if (criteria.getContextAction() == ContextAction.SWITCH_TOPIC
                || criteria.getReferenceType() == ReferenceType.PREVIOUS_TOPIC
                || criteria.getReferenceType() == ReferenceType.TOPIC_BY_DESCRIPTION) {
            var candidates = memory.topics().stream().filter(t -> {
                if (criteria.getTargetTopicId() != null) return t.id().equals(criteria.getTargetTopicId());
                if (criteria.getTopicDescription() != null && !criteria.getTopicDescription().isBlank()) {
                    String text = normalize(t.name() + " " + t.summary() + " " + t.context());
                    return text.contains(normalize(criteria.getTopicDescription()));
                }
                return !t.id().equals(memory.activeTopicId());
            }).toList();
            if (criteria.getReferenceType() == ReferenceType.PREVIOUS_TOPIC && !candidates.isEmpty()) {
                return new Selection(candidates.get(0), false, null);
            }
            if (candidates.size() == 1) return new Selection(candidates.get(0), false, null);
            return new Selection(memory.activeTopic(), false, "Anh/chị muốn quay lại chủ đề nào: "
                    + String.join(", ", (candidates.isEmpty() ? memory.topics() : candidates).stream()
                            .map(ConversationMemory.Topic::name).toList()) + "?");
        }
        return new Selection(memory.activeTopic(), criteria.getContextAction() == ContextAction.NEW_TOPIC
                && !"RECALL".equals(criteria.getIntent()), null);
    }

    static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace('đ', 'd').trim();
    }
}
