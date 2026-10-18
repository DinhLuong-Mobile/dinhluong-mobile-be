
package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.ConversationPrincipal;
import com.dinhluong.dlmstore.chatbot.entity.ChatbotConversation;
import com.dinhluong.dlmstore.chatbot.entity.ChatbotTopic;
import com.dinhluong.dlmstore.chatbot.repository.ChatbotTopicRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConversationTopicService {

    private final ChatbotTopicRepository topicRepository;
    private final ConversationSessionService sessionService;

    /**
     * Tạo chủ đề mới trong conversation.
     * Hỗ trợ cả user đăng nhập và khách vãng lai.
     */
    @Transactional
    public ChatbotTopic createTopic(
            String conversationId,
            ConversationPrincipal principal,
            String topicName
    ) {
        ChatbotConversation conversation =
                sessionService.getActiveConversation(
                        conversationId,
                        principal
                );

        if (topicName == null || topicName.isBlank()) {
            throw new IllegalArgumentException(
                    "Tên chủ đề không được để trống"
            );
        }

        String normalizedName = topicName.trim();

        if (normalizedName.length() > 150) {
            throw new IllegalArgumentException(
                    "Tên chủ đề không được vượt quá 150 ký tự"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        ChatbotTopic topic = ChatbotTopic.builder()
                .conversationId(conversation.getId())
                .topicName(normalizedName)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ChatbotTopic savedTopic =
                topicRepository.saveAndFlush(topic);

        // Chủ đề mới trở thành chủ đề đang hoạt động.
        conversation.activateTopic(savedTopic.getId());

        return savedTopic;
    }

    /**
     * Lấy chủ đề đang hoạt động.
     */
    @Transactional
    public Optional<ChatbotTopic> getActiveTopic(
            String conversationId,
            ConversationPrincipal principal
    ) {
        ChatbotConversation conversation =
                sessionService.getActiveConversation(
                        conversationId,
                        principal
                );

        Long activeTopicId =
                conversation.getActiveTopicId();

        if (activeTopicId == null) {
            return Optional.empty();
        }

        return topicRepository.findByIdAndConversationId(
                activeTopicId,
                conversationId
        );
    }

    /**
     * Chuyển sang chủ đề đã tồn tại.
     */
    @Transactional
    public ChatbotTopic switchTopic(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId
    ) {
        ChatbotConversation conversation =
                sessionService.getActiveConversation(
                        conversationId,
                        principal
                );

        if (topicId == null) {
            throw new IllegalArgumentException(
                    "Topic ID không được null"
            );
        }

        ChatbotTopic topic = topicRepository
                .findByIdAndConversationId(
                        topicId,
                        conversationId
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Chủ đề không tồn tại trong hội thoại"
                        )
                );

        conversation.activateTopic(topic.getId());

        return topic;
    }

    /**
     * Lấy danh sách chủ đề của conversation.
     */
    @Transactional
    public List<ChatbotTopic> getTopics(
            String conversationId,
            ConversationPrincipal principal
    ) {
        sessionService.getActiveConversation(
                conversationId,
                principal
        );

        return topicRepository
                .findByConversationIdOrderByUpdatedAtDesc(
                        conversationId
                );
    }

    /**
     * Lấy chủ đề theo ID, đảm bảo thuộc đúng conversation.
     */
    @Transactional
    public ChatbotTopic getTopic(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId
    ) {
        sessionService.getActiveConversation(
                conversationId,
                principal
        );

        if (topicId == null) {
            throw new IllegalArgumentException(
                    "Topic ID không được null"
            );
        }

        return topicRepository
                .findByIdAndConversationId(
                        topicId,
                        conversationId
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy chủ đề"
                        )
                );
    }
}
