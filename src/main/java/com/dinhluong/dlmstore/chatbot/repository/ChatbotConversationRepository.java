package com.dinhluong.dlmstore.chatbot.repository;

import com.dinhluong.dlmstore.chatbot.entity.ChatbotConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatbotConversationRepository
        extends JpaRepository<ChatbotConversation, String> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from ChatbotConversation c where c.id = :id")
    Optional<ChatbotConversation> findForUpdate(@org.springframework.data.repository.query.Param("id") String id);

    Optional<ChatbotConversation> findByIdAndUserId(
            String id,
            Long userId
    );
}