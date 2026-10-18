
package com.dinhluong.dlmstore.chatbot.dto;

import java.util.List;
import java.util.Map;

public record IntentConversationContext(
        Long activeTopicId,
        Map<String, Object> activeContext,
        List<TopicSummary> topics,
        String pendingQuestion,
        String pendingField,
        List<ConversationMemory.Turn> recentTurns,
        List<Long> lastComparedProductIds,
        Map<String, String> categoriesBySlug,
        List<String> availableBrands
) {
    public IntentConversationContext(Long activeTopicId, Map<String, Object> activeContext,
                                     List<TopicSummary> topics, String pendingQuestion, String pendingField,
                                     List<ConversationMemory.Turn> recentTurns, List<Long> comparedIds) {
        this(activeTopicId, activeContext, topics, pendingQuestion, pendingField, recentTurns, comparedIds, Map.of(), List.of());
    }
    public IntentConversationContext(Long activeTopicId, Map<String, Object> activeContext,
                                     List<TopicSummary> topics, String pendingQuestion, String pendingField) {
        this(activeTopicId, activeContext, topics, pendingQuestion, pendingField, List.of(), List.of(), Map.of(), List.of());
    }
    public record TopicSummary(
            Long id,
            String name,
            String summary
    ) {
    }
}
