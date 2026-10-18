package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.ConversationPrincipal;
import com.dinhluong.dlmstore.chatbot.enums.AiModel;
import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final ChatbotConversationOrchestrator orchestrator;

    public ChatBotResponse processUserMessage(
            ConversationPrincipal principal,
            String conversationId,
            String userMessage,
            AiModel model
    ) {
        return processUserMessage(principal, conversationId, userMessage, model, null, null);
    }

    public ChatBotResponse processUserMessage(ConversationPrincipal principal, String conversationId, String userMessage,
            AiModel model, Long selectedProductId, Long clarificationId) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException(
                    "Nội dung tin nhắn không được để trống"
            );
        }

        AiModel selectedModel =
                model != null ? model : AiModel.AUTO;
        if (userMessage.length() > 4000) throw new IllegalArgumentException("Tin nhắn không được vượt quá 4000 ký tự");
        if (conversationId != null && !conversationId.isBlank()) {
            try {
                if (!java.util.UUID.fromString(conversationId).toString().equals(conversationId)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Conversation ID không hợp lệ");
            }
        }

        if (selectedProductId == null && clarificationId == null)
            return orchestrator.process(principal, conversationId, userMessage.trim(), selectedModel);
        return orchestrator.process(
                principal,
                conversationId,
                userMessage.trim(),
                selectedModel, selectedProductId, clarificationId
        );
    }
}
