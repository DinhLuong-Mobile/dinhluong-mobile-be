package com.dinhluong.dlmstore.chatbot.exception;

public class AiProviderException
        extends RuntimeException {

    private final boolean retryable;

    public AiProviderException(
            String message,
            boolean retryable,
            Throwable cause
    ) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}