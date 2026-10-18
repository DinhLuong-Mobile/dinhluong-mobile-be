
package com.dinhluong.dlmstore.chatbot.dto;

import com.dinhluong.dlmstore.chatbot.enums.ClarificationType;

import java.time.LocalDateTime;
import java.util.Map;

public record CreateClarificationRequest(
        ClarificationType type,
        String fieldName,
        String originalMessage,
        String question,
        Map<String, Object> candidateData,
        LocalDateTime expiresAt
) {
}
