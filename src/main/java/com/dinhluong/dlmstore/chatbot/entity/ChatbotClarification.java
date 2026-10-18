
package com.dinhluong.dlmstore.chatbot.entity;

import com.dinhluong.dlmstore.chatbot.enums.ClarificationStatus;
import com.dinhluong.dlmstore.chatbot.enums.ClarificationType;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "chatbot_clarifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatbotClarification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "conversation_id",
            length = 36,
            nullable = false
    )
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    private String conversationId;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "clarification_type", nullable = false)
    private ClarificationType clarificationType;

    @Column(name = "field_name", length = 50)
    private String fieldName;

    @Column(name = "original_message", columnDefinition = "TEXT")
    private String originalMessage;

    @Column(name = "question", columnDefinition = "TEXT", nullable = false)
    private String question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_data", columnDefinition = "json")
    private Map<String, Object> candidateData;

    @Builder.Default
    @Column(name = "attempts", nullable = false)
    private Integer attempts = 1;

    @Builder.Default
    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 3;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false)
    private ClarificationStatus status =
            ClarificationStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolved_value", columnDefinition = "json")
    private Map<String, Object> resolvedValue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // Generated column in MySQL.
    // Hibernate must not insert or update this column.
    @Column(
            name = "pending_topic_id",
            insertable = false,
            updatable = false
    )
    private Long pendingTopicId;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void resolve(Map<String, Object> value) {
        this.resolvedValue = value;
        this.status = ClarificationStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = ClarificationStatus.CANCELLED;
    }

    public boolean isPending() {
        return status == ClarificationStatus.PENDING;
    }
}
