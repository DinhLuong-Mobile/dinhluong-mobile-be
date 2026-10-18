package com.dinhluong.dlmstore.chatbot.exception;

/** An unavailable conversation or clarification, rather than an internal system error. */
public class ConversationStateException extends IllegalStateException {
    public ConversationStateException(String message) {
        super(message);
    }
}
