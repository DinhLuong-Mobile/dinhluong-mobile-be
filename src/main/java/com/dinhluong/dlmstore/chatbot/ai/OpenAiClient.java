package com.dinhluong.dlmstore.chatbot.ai;

import com.dinhluong.dlmstore.chatbot.exception.AiProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiClient implements AiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.api.model}")
    private String model;

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public String generate(String prompt) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderException(
                    "OpenAI API Key chưa được cấu hình",
                    false,
                    null
            );
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", prompt
            );

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            apiUrl,
                            HttpMethod.POST,
                            request,
                            String.class
                    );

            JsonNode root = objectMapper.readTree(
                    response.getBody()
            );

            StringBuilder result = new StringBuilder();

            JsonNode output = root.path("output");

            if (output.isArray()) {
                for (JsonNode item : output) {
                    JsonNode content = item.path("content");

                    if (!content.isArray()) {
                        continue;
                    }

                    for (JsonNode part : content) {
                        if ("output_text".equals(
                                part.path("type").asText()
                        )) {
                            result.append(
                                    part.path("text").asText("")
                            );
                        }
                    }
                }
            }

            String answer = result.toString().trim();

            if (answer.isEmpty()) {
                throw new AiProviderException(
                        "OpenAI không trả về nội dung",
                        true,
                        null
                );
            }

            return answer;

        } catch (HttpStatusCodeException e) {

            int status = e.getStatusCode().value();

            boolean retryable =
                    status == 408 ||
                            status == 429 ||
                            status >= 500;

            throw new AiProviderException(
                    "OpenAI HTTP error: " + status,
                    retryable,
                    e
            );

        } catch (ResourceAccessException e) {

            throw new AiProviderException(
                    "Không thể kết nối OpenAI",
                    true,
                    e
            );

        } catch (AiProviderException e) {
            throw e;

        } catch (Exception e) {

            throw new AiProviderException(
                    "Không thể xử lý phản hồi OpenAI",
                    true,
                    e
            );
        }
    }
}