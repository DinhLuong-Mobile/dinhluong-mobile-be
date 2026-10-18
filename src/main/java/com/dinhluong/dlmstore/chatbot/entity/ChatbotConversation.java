
package com.dinhluong.dlmstore.chatbot.entity;

import com.dinhluong.dlmstore.chatbot.enums.ConversationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chatbot_conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatbotConversation {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    private String id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "guest_token_hash", length = 255)
    private String guestTokenHash;

    @Column(name = "active_topic_id")
    private Long activeTopicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ConversationStatus status =
            ConversationStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }

        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (lastMessageAt == null) {
            lastMessageAt = now;
        }
    }

    public void updateLastMessageTime() {
        lastMessageAt = LocalDateTime.now();
    }

    public void activateTopic(Long topicId) {
        this.activeTopicId = topicId;
    }
}
