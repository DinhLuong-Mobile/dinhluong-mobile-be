package com.dinhluong.dlmstore.chatbot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiJsonParser {

    private final ObjectMapper objectMapper;

    public <T> T parse(String raw, Class<T> clazz) {

        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "AI response rỗng"
            );
        }

        try {
            String json = raw.trim();

            if (json.startsWith("```")) {
                json = json.replaceFirst(
                        "^```(?:json)?\\s*",
                        ""
                );
                json = json.replaceFirst(
                        "\\s*```$",
                        ""
                );
            }

            return objectMapper.readValue(json, clazz);

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Không thể parse JSON từ AI",
                    e
            );
        }
    }
}
