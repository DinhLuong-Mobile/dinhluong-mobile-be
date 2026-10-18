
package com.dinhluong.dlmstore.chatbot.entity;

import com.dinhluong.dlmstore.chatbot.enums.ChatInputType;
import com.dinhluong.dlmstore.chatbot.enums.ChatProcessingStatus;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "chatbot_interactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatbotInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "conversation_id", length = 36)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    private String conversationId;

    @Column(name = "topic_id")
    private Long topicId;

    @Column(name = "clarification_id")
    private Long clarificationId;

    @Column(name = "intent", length = 30)
    private String intent;

    @Column(name = "context_action", length = 30)
    private String contextAction;

    @Column(name = "message_action", length = 30)
    private String messageAction;

    @Column(name = "ai_model", length = 20)
    private String aiModel;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "input_type", nullable = false)
    private ChatInputType inputType = ChatInputType.TEXT;

    @Column(name = "stt_raw_text", columnDefinition = "TEXT")
    private String sttRawText;

    @Column(name = "stt_confidence", precision = 5, scale = 4)
    private BigDecimal sttConfidence;

    @Column(name = "query", nullable = false, columnDefinition = "TEXT")
    private String query;

    @Column(name = "response", columnDefinition = "TEXT")
    private String response;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "presented_products", columnDefinition = "json")
    private List<Long> presentedProducts;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "processing_status", nullable = false)
    private ChatProcessingStatus processingStatus =
            ChatProcessingStatus.SUCCESS;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
