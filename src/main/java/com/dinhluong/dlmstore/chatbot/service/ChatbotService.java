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
public class ChatbotService {

    private final IntentExtractionService intentExtractionService;
    private final ChatbotProductSearchService productSearchService;
    private final AiResponseService aiResponseService;
    private final ProductSelectionService productSelectionService;
    private final ChatHistoryService chatHistoryService;

    public ChatBotResponse processUserMessage(
            Long userId,
            String userMessage,
            AiModel model
    ) {

        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException(
                    "Tin nhắn không được để trống"
            );
        }
        AiModel selectedModel =
                model == null ? AiModel.AUTO : model;
        GeminiFilterCriteria criteria =
                intentExtractionService.extract(userMessage,selectedModel);

        String intent = criteria.getIntent()
                .trim()
                .toUpperCase(Locale.ROOT);

        ChatBotResponse response;

        switch (intent) {

            case "OUT_OF_DOMAIN" -> response =
                    new ChatBotResponse(
                            "Dạ em là trợ lý tư vấn điện thoại DLM Store. "
                                    + "Em có thể hỗ trợ anh/chị tìm kiếm "
                                    + "và so sánh điện thoại ạ!",
                            List.of()
                    );

            case "CHAT" -> response =
                    new ChatBotResponse(
                            aiResponseService.generateChatResponse(
                                    userMessage,selectedModel
                            ),
                            List.of()
                    );

            case "SEARCH", "DETAIL", "COMPARE" -> {

                ChatbotSearchResult searchResult =
                        productSearchService.search(criteria);

                AiFilterResponse aiResult =
                        aiResponseService.generateProductResponse(
                                userMessage,
                                intent,
                                searchResult.aiProducts(),
                                searchResult.fallbackNote(),
                                selectedModel
                        );

                response = new ChatBotResponse(
                        aiResult.getAnswer(),
                        productSelectionService.select(
                                searchResult.uiProducts(),
                                aiResult.getProductIds(),
                                intent
                        )
                );
            }

            default -> throw new IllegalArgumentException(
                    "Intent không hợp lệ: " + intent
            );
        }

        chatHistoryService.save(
                userId,
                userMessage,
                response
        );

        return response;
    }
}