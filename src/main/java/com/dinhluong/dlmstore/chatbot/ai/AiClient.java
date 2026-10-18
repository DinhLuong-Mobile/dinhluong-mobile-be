package com.dinhluong.dlmstore.chatbot.ai;

public interface AiClient {

    String getProviderName();

    String generate(String prompt);
}