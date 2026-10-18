package com.dinhluong.dlmstore.chatbot.dto;

import com.dinhluong.dlmstore.dto.ChatProductDto;
import com.dinhluong.dlmstore.dto.ProductAiDto;

import java.util.List;

public record ChatbotSearchResult(
        List<ProductAiDto> aiProducts,
        List<ChatProductDto> uiProducts,
        String fallbackNote
) {
}