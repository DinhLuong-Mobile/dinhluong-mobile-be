package com.dinhluong.dlmstore.chatbot.exception;

/** Retains only previously committed conversation identifiers after a failed turn. */
public class ConversationAiException extends AiProviderException {
    private final String conversationId;
    private final Long topicId;

    public ConversationAiException(AiProviderException cause, String conversationId, Long topicId) {
        super(cause.getMessage(), cause.isRetryable(), cause);
        this.conversationId = conversationId;
        this.topicId = topicId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public Long getTopicId() {
        return topicId;
    }
}
