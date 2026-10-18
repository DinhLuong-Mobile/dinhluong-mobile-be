
package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.ChatbotSearchResult;
import com.dinhluong.dlmstore.chatbot.enums.AiModel;
import com.dinhluong.dlmstore.dto.GeminiFilterCriteria;
import com.dinhluong.dlmstore.dto.responses.AiFilterResponse;
import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ChatbotAnswerService {

    private final ChatbotProductSearchService productSearchService;
    private final AiResponseService aiResponseService;
    private final ProductSelectionService productSelectionService;

    public ChatBotResponse generate(
            String userMessage,
            GeminiFilterCriteria criteria,
            AiModel model
    ) {
        String intent = criteria.getIntent()
                .trim()
                .toUpperCase(Locale.ROOT);

        return switch (intent) {

            case "OUT_OF_DOMAIN" -> new ChatBotResponse(
                    "Dạ em là trợ lý tư vấn sản phẩm DLM Store. "
                            + "Em có thể hỗ trợ anh/chị tìm kiếm "
                            + "và so sánh sản phẩm trong các danh mục của cửa hàng ạ!",
                    List.of()
            );

            case "CHAT" -> new ChatBotResponse(
                    aiResponseService.generateChatResponse(
                            userMessage,
                            model
                    ),
                    List.of()
            );

            case "SEARCH", "DETAIL", "COMPARE" ->
                    generateProductAnswer(
                            userMessage,
                            criteria,
                            intent,
                            model
                    );

            default -> throw new IllegalArgumentException(
                    "Intent không hợp lệ: " + intent
            );
        };
    }

    private ChatBotResponse generateProductAnswer(
            String userMessage,
            GeminiFilterCriteria criteria,
            String intent,
            AiModel model
    ) {
        ChatbotSearchResult searchResult =
                productSearchService.search(criteria);

        AiFilterResponse aiResult =
                aiResponseService.generateProductResponse(
                        userMessage,
                        intent,
                        searchResult.aiProducts(),
                        searchResult.fallbackNote(),
                        model
                );

        return new ChatBotResponse(
                aiResult.getAnswer(),
                productSelectionService.select(
                        searchResult.uiProducts(),
                        aiResult.getProductIds(),
                        intent
                )
        );
    }
}
