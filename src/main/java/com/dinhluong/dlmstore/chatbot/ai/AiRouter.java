
package com.dinhluong.dlmstore.chatbot.ai;

import com.dinhluong.dlmstore.chatbot.enums.AiModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class AiRouter {

    private final OpenAiClient openAiClient;
    private final GeminiAiClient geminiAiClient;
    private final OllamaAiClient ollamaAiClient;
    private final FallbackAiClient fallbackAiClient;

    public String generate(AiModel model, String prompt) {
        AiModel selected = normalize(model);

        if (selected == AiModel.AUTO) {
            return fallbackAiClient.generate(prompt);
        }

        return resolve(selected).generate(prompt);
    }

    public <T> T generateAndValidate(
            AiModel model,
            String prompt,
            Function<String, T> validator
    ) {
        AiModel selected = normalize(model);

        if (selected == AiModel.AUTO) {
            return fallbackAiClient.generateAndValidate(
                    prompt,
                    validator
            );
        }

        String raw = resolve(selected).generate(prompt);

        return validator.apply(raw);
    }

    private AiClient resolve(AiModel model) {
        return switch (model) {
            case OPENAI -> openAiClient;
            case GEMINI -> geminiAiClient;
            case OLLAMA -> ollamaAiClient;
            case AUTO -> throw new IllegalArgumentException(
                    "AUTO phải được xử lý bằng fallback"
            );
        };
    }

    private AiModel normalize(AiModel model) {
        return model == null ? AiModel.AUTO : model;
    }
}
