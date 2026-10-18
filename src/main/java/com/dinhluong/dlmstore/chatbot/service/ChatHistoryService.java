
package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.entity.ChatbotInteraction;
import com.dinhluong.dlmstore.chatbot.enums.AiModel;
import com.dinhluong.dlmstore.chatbot.enums.ChatInputType;
import com.dinhluong.dlmstore.chatbot.enums.ChatProcessingStatus;
import com.dinhluong.dlmstore.chatbot.repository.ChatbotInteractionRepository;
import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatbotInteractionRepository interactionRepository;

    @Transactional
    public ChatbotInteraction save(
            Long userId,
            String conversationId,
            Long topicId,
            String userMessage,
            ChatBotResponse response,
            String intent,
            String contextAction,
            String messageAction,
            AiModel model
    ) {
        if (response == null) {
            throw new IllegalArgumentException(
                    "Chatbot response không được null"
            );
        }

        ChatbotInteraction interaction =
                ChatbotInteraction.builder()
                        .userId(userId)
                        .conversationId(conversationId)
                        .topicId(topicId)
                        .query(userMessage)
                        .response(response.getAnswer())
                        .presentedProducts(response.getProducts() == null ? java.util.List.of()
                                : response.getProducts().stream()
                                        .map(com.dinhluong.dlmstore.dto.ChatProductDto::getId).toList())
                        .intent(intent)
                        .contextAction(contextAction)
                        .messageAction(messageAction)
                        .aiModel(model == null
                                ? AiModel.AUTO.name()
                                : model.name())
                        .inputType(ChatInputType.TEXT)
                        .processingStatus(
                                response.isRequiresClarification()
                                        ? ChatProcessingStatus.CLARIFICATION
                                        : ChatProcessingStatus.SUCCESS
                        )
                        .clarificationId(
                                response.getClarificationId()
                        )
                        .build();

        return interactionRepository.save(interaction);
    }
}
