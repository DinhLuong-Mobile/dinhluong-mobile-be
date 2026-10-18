package com.dinhluong.dlmstore.chatbot.ai;

import com.dinhluong.dlmstore.chatbot.exception.AiProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OllamaAiClient implements AiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ollama.api.url}")
    private String apiUrl;

    @Value("${ollama.api.model}")
    private String model;

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    public String generate(String prompt) {

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", prompt
                            )
                    ),
                    "stream", false
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

            String answer = root.path("message")
                    .path("content")
                    .asText("")
                    .trim();

            if (answer.isBlank()) {
                throw new AiProviderException(
                        "Ollama trả về nội dung rỗng",
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
                    "Ollama HTTP error: " + status,
                    retryable,
                    e
            );

        } catch (ResourceAccessException e) {

            throw new AiProviderException(
                    "Không thể kết nối Ollama",
                    true,
                    e
            );

        } catch (AiProviderException e) {
            throw e;

        } catch (Exception e) {

            throw new AiProviderException(
                    "Không thể xử lý phản hồi Ollama",
                    true,
                    e
            );
        }
    }
}