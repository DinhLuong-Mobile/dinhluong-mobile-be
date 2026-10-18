package com.dinhluong.dlmstore.chatbot.repository;

import com.dinhluong.dlmstore.chatbot.entity.ChatbotInteraction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatbotInteractionRepository
        extends JpaRepository<ChatbotInteraction, Long> {
    List<ChatbotInteraction> findByConversationIdOrderByIdDesc(String conversationId, Pageable pageable);
    List<ChatbotInteraction> findByConversationIdAndIntentAndProcessingStatusOrderByIdDesc(String conversationId,
            String intent, com.dinhluong.dlmstore.chatbot.enums.ChatProcessingStatus status, Pageable pageable);

    List<ChatbotInteraction>
    findByConversationIdOrderByCreatedAtDescIdDesc(
            String conversationId,
            Pageable pageable
    );

    List<ChatbotInteraction>
    findByConversationIdAndTopicIdOrderByCreatedAtDescIdDesc(
            String conversationId,
            Long topicId,
            Pageable pageable
    );

    @Query("SELECT COUNT(c) FROM ChatbotInteraction c WHERE c.createdAt >= :startDate AND c.createdAt <= :endDate")
    long countInteractions(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
