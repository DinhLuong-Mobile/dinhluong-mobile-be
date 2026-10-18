
package com.dinhluong.dlmstore.chatbot.repository;

import com.dinhluong.dlmstore.chatbot.entity.ChatbotClarification;
import com.dinhluong.dlmstore.chatbot.enums.ClarificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatbotClarificationRepository
        extends JpaRepository<ChatbotClarification, Long> {
    Optional<ChatbotClarification> findTopByConversationIdAndTopicIdOrderByIdDesc(String conversationId, Long topicId);

    Optional<ChatbotClarification>
    findByConversationIdAndTopicIdAndStatus(
            String conversationId,
            Long topicId,
            ClarificationStatus status
    );

    Optional<ChatbotClarification>
    findByIdAndConversationIdAndTopicId(
            Long id,
            String conversationId,
            Long topicId
    );
}
