
package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.ConversationPrincipal;
import com.dinhluong.dlmstore.chatbot.dto.CreateClarificationRequest;
import com.dinhluong.dlmstore.chatbot.entity.ChatbotClarification;
import com.dinhluong.dlmstore.chatbot.enums.ClarificationStatus;
import com.dinhluong.dlmstore.chatbot.repository.ChatbotClarificationRepository;
import com.dinhluong.dlmstore.chatbot.repository.ChatbotTopicRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClarificationService {

    private final ChatbotClarificationRepository clarificationRepository;
    private final ChatbotTopicRepository topicRepository;
    private final ConversationSessionService sessionService;

    @Transactional
    public Optional<ChatbotClarification> getPending(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId
    ) {
        validateAccess(conversationId, principal, topicId);

        Optional<ChatbotClarification> pending =
                clarificationRepository
                        .findByConversationIdAndTopicIdAndStatus(
                                conversationId,
                                topicId,
                                ClarificationStatus.PENDING
                        );

        if (pending.isEmpty()) {
            return Optional.empty();
        }

        ChatbotClarification clarification = pending.get();

        if (isExpired(clarification)) {
            clarification.setStatus(ClarificationStatus.EXPIRED);
            return Optional.empty();
        }

        return Optional.of(clarification);
    }

    @Transactional
    public ChatbotClarification create(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId,
            CreateClarificationRequest request
    ) {
        validateAccess(conversationId, principal, topicId);

        if (request == null
                || request.type() == null
                || request.question() == null
                || request.question().isBlank()) {
            throw new IllegalArgumentException(
                    "Thông tin clarification không hợp lệ"
            );
        }

        if (request.question().length() > 1000
                || (request.expiresAt() != null && !request.expiresAt().isAfter(LocalDateTime.now()))) {
            throw new IllegalArgumentException("Clarification question or expiration is invalid");
        }

        Optional<ChatbotClarification> existing =
                getPending(conversationId, principal, topicId);

        if (existing.isPresent()) {
            throw new com.dinhluong.dlmstore.chatbot.exception.ConversationStateException(
                    "Topic đang có câu hỏi chờ trả lời"
            );
        }

        if (request.fieldName() != null
                && request.fieldName().length() > 50) {
            throw new IllegalArgumentException(
                    "fieldName quá dài"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        ChatbotClarification clarification =
                ChatbotClarification.builder()
                        .conversationId(conversationId)
                        .topicId(topicId)
                        .clarificationType(request.type())
                        .fieldName(request.fieldName())
                        .originalMessage(request.originalMessage())
                        .question(request.question())
                        .candidateData(request.candidateData())
                        .attempts(1)
                        .maxAttempts(3)
                        .status(ClarificationStatus.PENDING)
                        .createdAt(now)
                        .updatedAt(now)
                        .expiresAt(request.expiresAt())
                        .build();

        return clarificationRepository.save(clarification);
    }

    @Transactional
    public ChatbotClarification resolve(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId,
            Long clarificationId,
            Map<String, Object> resolvedValue
    ) {
        ChatbotClarification clarification =
                getPendingById(
                        conversationId,
                        principal,
                        topicId,
                        clarificationId
                );

        if (isExpiredAndMark(clarification)) {
            return clarification;
        }

        if (resolvedValue == null || resolvedValue.isEmpty()) {
            throw new IllegalArgumentException(
                    "Kết quả làm rõ không được để trống"
            );
        }

        clarification.resolve(resolvedValue);

        return clarification;
    }

    @Transactional
    public ChatbotClarification recordFailedAttempt(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId,
            Long clarificationId
    ) {
        ChatbotClarification clarification =
                getPendingById(
                        conversationId,
                        principal,
                        topicId,
                        clarificationId
                );

        if (isExpiredAndMark(clarification)) {
            return clarification;
        }

        int attempts = clarification.getAttempts();
        int maxAttempts = clarification.getMaxAttempts();

        if (attempts + 1 >= maxAttempts) {
            clarification.setAttempts(attempts + 1);
            clarification.setStatus(ClarificationStatus.CANCELLED);
            return clarification;
        }

        clarification.setAttempts(attempts + 1);

        return clarification;
    }

    @Transactional
    public ChatbotClarification cancel(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId,
            Long clarificationId
    ) {
        ChatbotClarification clarification =
                getPendingById(
                        conversationId,
                        principal,
                        topicId,
                        clarificationId
                );

        if (isExpiredAndMark(clarification)) {
            return clarification;
        }

        clarification.cancel();

        return clarification;
    }

    private ChatbotClarification getPendingById(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId,
            Long clarificationId
    ) {
        validateAccess(conversationId, principal, topicId);

        if (clarificationId == null) {
            throw new IllegalArgumentException(
                    "Clarification ID không hợp lệ"
            );
        }

        ChatbotClarification clarification =
                clarificationRepository
                        .findByIdAndConversationIdAndTopicId(
                                clarificationId,
                                conversationId,
                                topicId
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Không tìm thấy clarification"
                                )
                        );

        if (clarification.getStatus() != ClarificationStatus.PENDING) {
            throw new com.dinhluong.dlmstore.chatbot.exception.ConversationStateException(
                    "Clarification không còn chờ trả lời"
            );
        }

        return clarification;
    }

    private boolean isExpiredAndMark(
            ChatbotClarification clarification
    ) {
        if (isExpired(clarification)) {
            clarification.setStatus(ClarificationStatus.EXPIRED);
            return true;
        }

        return false;
    }

    private boolean isExpired(
            ChatbotClarification clarification
    ) {
        return clarification.getExpiresAt() != null
                && !clarification.getExpiresAt()
                .isAfter(LocalDateTime.now());
    }

    private void validateAccess(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId
    ) {
        sessionService.getActiveConversation(
                conversationId,
                principal
        );

        if (topicId == null
                || topicRepository
                .findByIdAndConversationId(
                        topicId,
                        conversationId
                )
                .isEmpty()) {
            throw new IllegalArgumentException(
                    "Topic không thuộc conversation"
            );
        }
    }
}
