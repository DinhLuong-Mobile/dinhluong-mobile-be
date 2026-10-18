
package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.ConversationPrincipal;
import com.dinhluong.dlmstore.chatbot.entity.ChatbotConversation;
import com.dinhluong.dlmstore.chatbot.enums.ConversationStatus;
import com.dinhluong.dlmstore.chatbot.repository.ChatbotConversationRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationSessionService {

    private static final int SESSION_EXPIRY_DAYS = 7;

    private final ChatbotConversationRepository conversationRepository;
    private final GuestSessionService guestSessionService;

    @Transactional
    public ChatbotConversation createConversation(
            ConversationPrincipal principal
    ) {
        validatePrincipal(principal);

        LocalDateTime now = LocalDateTime.now();

        ChatbotConversation conversation =
                ChatbotConversation.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(principal.userId())
                        .guestTokenHash(
                                principal.isAuthenticated()
                                        ? null
                                        : guestSessionService.hashToken(
                                        principal.guestToken()
                                )
                        )
                        .status(ConversationStatus.ACTIVE)
                        .createdAt(now)
                        .lastMessageAt(now)
                        .expiresAt(
                                now.plusDays(SESSION_EXPIRY_DAYS)
                        )
                        .build();

        return conversationRepository.save(conversation);
    }

    @Transactional
    public ChatbotConversation getActiveConversation(
            String conversationId,
            ConversationPrincipal principal
    ) {
        validatePrincipal(principal);

        if (conversationId == null
                || conversationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Conversation ID không hợp lệ"
            );
        }

        ChatbotConversation conversation =
                conversationRepository
                        .findForUpdate(conversationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Không tìm thấy phiên hội thoại"
                                )
                        );

        validateOwnership(conversation, principal);
        validateActive(conversation);

        return conversation;
    }

    @Transactional
    public ChatbotConversation getOrCreateConversation(
            String conversationId,
            ConversationPrincipal principal
    ) {
        if (conversationId == null
                || conversationId.isBlank()) {
            return createConversation(principal);
        }

        return getActiveConversation(
                conversationId,
                principal
        );
    }

    @Transactional
    public void updateActivity(
            String conversationId,
            ConversationPrincipal principal
    ) {
        ChatbotConversation conversation =
                getActiveConversation(
                        conversationId,
                        principal
                );

        LocalDateTime now = LocalDateTime.now();

        conversation.setLastMessageAt(now);
        conversation.setExpiresAt(
                now.plusDays(SESSION_EXPIRY_DAYS)
        );
    }

    @Transactional
    public void closeConversation(
            String conversationId,
            ConversationPrincipal principal
    ) {
        ChatbotConversation conversation =
                getActiveConversation(
                        conversationId,
                        principal
                );

        conversation.setStatus(
                ConversationStatus.CLOSED
        );
    }

    /** Called in its own short transaction; the caller throws only after this update has committed. */
    @Transactional
    public boolean markExpiredIfOwned(String conversationId, ConversationPrincipal principal) {
        if (conversationId == null || conversationId.isBlank()) return false;
        validatePrincipal(principal);
        var conversation = conversationRepository.findForUpdate(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiên hội thoại"));
        validateOwnership(conversation, principal);
        if (conversation.getStatus() == ConversationStatus.EXPIRED) return true;
        if (conversation.getStatus() == ConversationStatus.ACTIVE && conversation.getExpiresAt() != null
                && !conversation.getExpiresAt().isAfter(LocalDateTime.now())) {
            conversation.setStatus(ConversationStatus.EXPIRED);
            return true;
        }
        return false;
    }

    private void validatePrincipal(
            ConversationPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalArgumentException(
                    "Thiếu thông tin chủ sở hữu hội thoại"
            );
        }

        if (!principal.isAuthenticated()
                && (principal.guestToken() == null
                || principal.guestToken().isBlank())) {
            throw new IllegalArgumentException(
                    "Guest token không hợp lệ"
            );
        }
    }

    private void validateOwnership(
            ChatbotConversation conversation,
            ConversationPrincipal principal
    ) {
        if (principal.isAuthenticated()) {

            if (!principal.userId().equals(
                    conversation.getUserId()
            )) {
                throw new SecurityException(
                        "Bạn không có quyền truy cập hội thoại"
                );
            }

            return;
        }

        if (conversation.getUserId() != null
                || !guestSessionService.matches(
                principal.guestToken(),
                conversation.getGuestTokenHash()
        )) {
            throw new SecurityException(
                    "Bạn không có quyền truy cập hội thoại"
            );
        }
    }

    private void validateActive(
            ChatbotConversation conversation
    ) {
        if (conversation.getStatus()
                != ConversationStatus.ACTIVE) {
            throw new com.dinhluong.dlmstore.chatbot.exception.ConversationStateException(
                    "Phiên hội thoại đã đóng hoặc hết hạn"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (conversation.getExpiresAt() != null
                && !conversation.getExpiresAt().isAfter(now)) {
            throw new com.dinhluong.dlmstore.chatbot.exception.ConversationStateException(
                    "Phiên hội thoại đã hết hạn"
            );
        }
    }
}
