package com.dinhluong.dlmstore.chatbot.dto;

public record ConversationPrincipal(
        Long userId,
        String guestToken
) {
    @Override
    public String toString() {
        return "ConversationPrincipal[userId=" + userId + ", guestToken=<redacted>]";
    }

    public boolean isAuthenticated() {
        return userId != null;
    }
}
