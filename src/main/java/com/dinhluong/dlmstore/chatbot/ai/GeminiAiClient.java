package com.dinhluong.dlmstore.chatbot.ai;

import com.dinhluong.dlmstore.chatbot.exception.AiProviderException;
import com.dinhluong.dlmstore.config.GeminiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAiClient implements AiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final GeminiProperties geminiProperties;

    private final AtomicInteger currentIndex = new AtomicInteger(0);

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public String generate(String prompt) {

        List<String> apiKeys = geminiProperties.getKeys();

        if (apiKeys == null || apiKeys.isEmpty()) {
            throw new AiProviderException(
                    "Gemini API Key chưa được cấu hình",
                    false,
                    null
            );
        }

        int totalKeys = apiKeys.size();

        for (int attempt = 0; attempt < totalKeys; attempt++) {

            String apiKey = getNextApiKey(apiKeys);

            try {
                String response = callGemini(prompt, apiKey);

                if (response == null || response.isBlank()) {
                    throw new AiProviderException(
                            "Gemini trả về nội dung rỗng",
                            true,
                            null
                    );
                }

                return response;

            } catch (HttpClientErrorException.TooManyRequests e) {

                log.warn(
                        "Gemini rate limit 429. Attempt {}/{}",
                        attempt + 1,
                        totalKeys
                );

                // Thử API Key tiếp theo nếu còn.
                // Các key cùng project có thể dùng chung quota.

            } catch (HttpStatusCodeException e) {

                int status = e.getStatusCode().value();

                boolean retryable =
                        status == 429 ||
                                status == 500 ||
                                status == 502 ||
                                status == 503 ||
                                status == 504;

                log.error(
                        "Gemini HTTP error: {}",
                        status
                );

                throw new AiProviderException(
                        "Gemini HTTP error: " + status,
                        retryable,
                        e
                );

            } catch (ResourceAccessException e) {

                throw new AiProviderException(
                        "Không thể kết nối Gemini",
                        true,
                        e
                );

            } catch (AiProviderException e) {

                throw e;

            } catch (Exception e) {

                throw new AiProviderException(
                        "Lỗi không xác định từ Gemini",
                        false,
                        e
                );
            }
        }

        throw new AiProviderException(
                "Gemini đã hết hạn mức API Key",
                true,
                null
        );
    }

    private String callGemini(
            String prompt,
            String apiKey
    ) throws Exception {

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", prompt)
                                )
                        )
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Gemini hỗ trợ API Key qua header.
        // Tránh đưa key vào URL.
        headers.set("x-goog-api-key", apiKey);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                geminiProperties.getUrl(),
                HttpMethod.POST,
                request,
                String.class
        );

        String responseBody = response.getBody();

        if (responseBody == null || responseBody.isBlank()) {
            throw new AiProviderException(
                    "Gemini trả về response rỗng",
                    true,
                    null
            );
        }

        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode candidates = root.path("candidates");

        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new AiProviderException(
                    "Gemini không trả về candidate hợp lệ",
                    true,
                    null
            );
        }

        JsonNode parts = candidates.get(0)
                .path("content")
                .path("parts");

        if (!parts.isArray() || parts.isEmpty()) {
            throw new AiProviderException(
                    "Gemini không trả về content hợp lệ",
                    true,
                    null
            );
        }

        String text = parts.get(0)
                .path("text")
                .asText("");

        if (text.isBlank()) {
            throw new AiProviderException(
                    "Gemini trả về text rỗng",
                    true,
                    null
            );
        }

        return text;
    }

    private String getNextApiKey(List<String> apiKeys) {

        int index = Math.floorMod(
                currentIndex.getAndIncrement(),
                apiKeys.size()
        );

        return apiKeys.get(index);
    }
}