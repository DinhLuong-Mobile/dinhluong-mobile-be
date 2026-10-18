
package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.entity.ChatbotInteraction;
import com.dinhluong.dlmstore.repository.ChatbotInteractionRepository;
import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatbotInteractionRepository interactionRepository;

    @Transactional
    public void save(
            Long userId,
            String userMessage,
            ChatBotResponse response
    ) {
        if (response == null) {
            return;
        }

        try {
            ChatbotInteraction interaction =
                    ChatbotInteraction.builder()
                            .userId(userId)
                            .query(userMessage)
                            .response(response.getAnswer())
                            .build();

            interactionRepository.save(interaction);

            log.debug(
                    "Đã lưu lịch sử chatbot cho userId={}",
                    userId
            );

        } catch (Exception e) {
            log.error(
                    "Không thể lưu lịch sử chatbot cho userId={}",
                    userId,
                    e
            );
        }
    }
}
