package com.dinhluong.dlmstore.chatbot.repository;

import com.dinhluong.dlmstore.chatbot.entity.ChatbotTopic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatbotTopicRepository
        extends JpaRepository<ChatbotTopic, Long> {
    List<ChatbotTopic> findByConversationIdOrderByUpdatedAtDesc(String conversationId,
                                                            org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select t from ChatbotTopic t where t.conversationId = :conversationId "
            + "and (lower(t.topicName) like lower(concat('%', :description, '%')) "
            + "or lower(t.contextSummary) like lower(concat('%', :description, '%'))) order by t.updatedAt desc, t.id desc")
    List<ChatbotTopic> findRelated(@org.springframework.data.repository.query.Param("conversationId") String conversationId,
                                  @org.springframework.data.repository.query.Param("description") String description,
                                  org.springframework.data.domain.Pageable pageable);

    List<ChatbotTopic> findByConversationIdOrderByUpdatedAtDesc(
            String conversationId
    );

    Optional<ChatbotTopic> findByIdAndConversationId(
            Long id,
            String conversationId
    );
}
