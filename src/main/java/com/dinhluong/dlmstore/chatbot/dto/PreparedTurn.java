package com.dinhluong.dlmstore.chatbot.dto;

import com.dinhluong.dlmstore.dto.GeminiFilterCriteria;
import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;
import java.util.List;
import java.util.Map;

public record PreparedTurn(ConversationMemory memory, GeminiFilterCriteria criteria, Long selectedTopicId,
                           boolean newTopic, Map<String, Object> context, ChatBotResponse response,
                           String clarificationQuestion, List<Long> candidateIds, boolean variantResponse) {
    public PreparedTurn(ConversationMemory memory, GeminiFilterCriteria criteria, Long selectedTopicId,
                        boolean newTopic, Map<String,Object> context, ChatBotResponse response,
                        String question, List<Long> candidates) {
        this(memory, criteria, selectedTopicId, newTopic, context, response, question, candidates, false);
    }
}
