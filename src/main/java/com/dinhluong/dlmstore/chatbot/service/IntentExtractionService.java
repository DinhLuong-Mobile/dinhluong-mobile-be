package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.ai.AiJsonParser;
import com.dinhluong.dlmstore.chatbot.ai.AiRouter;
import com.dinhluong.dlmstore.chatbot.ai.FallbackAiClient;
import com.dinhluong.dlmstore.chatbot.enums.AiModel;
import com.dinhluong.dlmstore.chatbot.enums.ChatIntent;
import com.dinhluong.dlmstore.chatbot.prompt.IntentPromptBuilder;
import com.dinhluong.dlmstore.dto.GeminiFilterCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class IntentExtractionService {

    private final IntentPromptBuilder promptBuilder;
    private final AiJsonParser jsonParser;
    private final AiRouter aiRouter;
    public GeminiFilterCriteria extract(String userMessage, AiModel model) {

        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException(
                    "Nội dung tin nhắn không được rỗng"
            );
        }

        String prompt = promptBuilder.build(userMessage);

        return aiRouter.generateAndValidate(
                model,
                prompt,
                this::parseAndValidate
        );
    }

    private GeminiFilterCriteria parseAndValidate(String raw) {

        GeminiFilterCriteria criteria =
                jsonParser.parse(raw, GeminiFilterCriteria.class);

        if (criteria == null || criteria.getIntent() == null) {
            throw new IllegalArgumentException(
                    "AI không trả về intent"
            );
        }

        boolean validIntent = Arrays.stream(ChatIntent.values())
                .anyMatch(intent ->
                        intent.name().equalsIgnoreCase(
                                criteria.getIntent()
                        )
                );

        if (!validIntent) {
            throw new IllegalArgumentException(
                    "Intent không hợp lệ: " + criteria.getIntent()
            );
        }

        if (criteria.getMinPrice() != null
                && criteria.getMinPrice().signum() < 0) {
            throw new IllegalArgumentException(
                    "minPrice không hợp lệ"
            );
        }

        if (criteria.getMaxPrice() != null
                && criteria.getMaxPrice().signum() < 0) {
            throw new IllegalArgumentException(
                    "maxPrice không hợp lệ"
            );
        }

        if (criteria.getMinPrice() != null
                && criteria.getMaxPrice() != null
                && criteria.getMinPrice().compareTo(
                criteria.getMaxPrice()
        ) > 0) {
            throw new IllegalArgumentException(
                    "minPrice lớn hơn maxPrice"
            );
        }

        if ("DETAIL".equalsIgnoreCase(criteria.getIntent())
                && (criteria.getTargetProductName() == null
                || criteria.getTargetProductName().isBlank())) {
            throw new IllegalArgumentException(
                    "DETAIL thiếu tên sản phẩm"
            );
        }

        if ("COMPARE".equalsIgnoreCase(criteria.getIntent())
                && (criteria.getCompareProductNames() == null
                || criteria.getCompareProductNames().size() < 2)) {
            throw new IllegalArgumentException(
                    "COMPARE cần ít nhất hai sản phẩm"
            );
        }

        return criteria;
    }
}