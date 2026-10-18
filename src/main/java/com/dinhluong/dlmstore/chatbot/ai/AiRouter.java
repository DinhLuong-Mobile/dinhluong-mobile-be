
package com.dinhluong.dlmstore.chatbot.ai;

import com.dinhluong.dlmstore.chatbot.enums.AiModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class AiRouter {

    private final OpenAiClient openAiClient;
    private final GeminiAiClient geminiAiClient;
    private final OllamaAiClient ollamaAiClient;
    private final FallbackAiClient fallbackAiClient;
    private final AiUsageGuard usage;

    @org.springframework.beans.factory.annotation.Autowired
    public AiRouter(OpenAiClient openAiClient, GeminiAiClient geminiAiClient, OllamaAiClient ollamaAiClient,
                    FallbackAiClient fallbackAiClient, AiUsageGuard usage) {
        this.openAiClient = openAiClient; this.geminiAiClient = geminiAiClient;
        this.ollamaAiClient = ollamaAiClient; this.fallbackAiClient = fallbackAiClient; this.usage = usage;
    }

    public AiRouter(OpenAiClient openAiClient, GeminiAiClient geminiAiClient, OllamaAiClient ollamaAiClient,
                    FallbackAiClient fallbackAiClient) {
        this(openAiClient, geminiAiClient, ollamaAiClient, fallbackAiClient, new AiUsageGuard());
    }

    public String generate(AiModel model, String prompt) {
        return usage.call(() -> generateText(model, prompt));
    }

    private String generateText(AiModel model, String prompt) {
        validatePrompt(prompt);
        AiModel selected = normalize(model);

        if (selected == AiModel.AUTO) {
            return fallbackAiClient.generate(prompt);
        }

        String response = resolve(selected).generate(prompt);
        if (response == null || response.isBlank() || response.length() > 32000) {
            throw new com.dinhluong.dlmstore.chatbot.exception.AiProviderException("Empty AI response", true, null);
        }
        return response;
    }

    public <T> T generateAndValidate(
            AiModel model,
            String prompt,
            Function<String, T> validator
    ) {
        return usage.call(() -> generateValidated(model, prompt, validator));
    }

    private <T> T generateValidated(AiModel model, String prompt, Function<String, T> validator) {
        validatePrompt(prompt);
        AiModel selected = normalize(model);

        if (selected == AiModel.AUTO) {
            return fallbackAiClient.generateAndValidate(
                    prompt,
                    validator
            );
        }

        String raw = resolve(selected).generate(prompt);
        if (raw == null || raw.isBlank() || raw.length() > 32000) {
            throw new com.dinhluong.dlmstore.chatbot.exception.AiProviderException("Invalid AI response size", true, null);
        }

        try {
            T result = validator.apply(raw);
            if (result == null) throw new IllegalArgumentException("AI returned null");
            return result;
        } catch (IllegalArgumentException e) {
            throw new com.dinhluong.dlmstore.chatbot.exception.AiProviderException(
                    "Invalid AI response", true, e);
        }
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

    private static void validatePrompt(String prompt) {
        if (prompt == null || prompt.isBlank() || prompt.length() > 32000) {
            throw new IllegalArgumentException("AI prompt must contain between 1 and 32000 characters");
        }
    }

    private AiModel normalize(AiModel model) {
        return model == null ? AiModel.AUTO : model;
    }
}
