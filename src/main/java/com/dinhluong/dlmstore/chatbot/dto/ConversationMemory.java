package com.dinhluong.dlmstore.chatbot.dto;

import java.util.List;
import java.util.Map;

/** Authorized, bounded snapshot. Never contains credentials or an entity graph. */
public record ConversationMemory(String conversationId, Long activeTopicId, Long revision,
                                 List<Topic> topics, List<Turn> recentTurns, List<Turn> productTurns,
                                 Pending pending) {
    public record Topic(Long id, String name, Map<String, Object> context, String summary) { }
    public record Turn(Long id, Long topicId, String intent, String query, String answer, List<Long> productIds) { }
    public record Pending(Long id, Long topicId, String question, String originalMessage,
                          Map<String, Object> candidateData, int attempts, int maxAttempts) { }

    public Topic activeTopic() {
        return topics.stream().filter(t -> t.id().equals(activeTopicId)).findFirst().orElse(null);
    }
}
