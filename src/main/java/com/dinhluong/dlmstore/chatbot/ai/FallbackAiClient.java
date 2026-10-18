
package com.dinhluong.dlmstore.chatbot.ai;

import com.dinhluong.dlmstore.chatbot.exception.AiProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class FallbackAiClient {

    private final List<AiClient> clients;

    public FallbackAiClient(
            List<AiClient> providers,
            @Value("${ai.providers.order}")
            List<String> providerOrder
    ) {
        Map<String, AiClient> providerMap = providers.stream()
                .collect(Collectors.toMap(
                        provider -> provider.getProviderName()
                                .trim()
                                .toLowerCase(Locale.ROOT),
                        Function.identity()
                ));

        if (providerOrder == null || providerOrder.isEmpty()) {
            throw new IllegalStateException(
                    "Chưa cấu hình danh sách AI provider"
            );
        }

        List<AiClient> ordered = new ArrayList<>();
        Set<String> registeredNames = new HashSet<>();

        for (String name : providerOrder) {

            String normalizedName = name.trim()
                    .toLowerCase(Locale.ROOT);

            AiClient client = providerMap.get(normalizedName);

            if (client == null) {
                throw new IllegalStateException(
                        "AI provider không tồn tại: " + name
                );
            }

            if (!registeredNames.add(normalizedName)) {
                throw new IllegalStateException(
                        "AI provider bị trùng: " + name
                );
            }

            ordered.add(client);
        }

        this.clients = List.copyOf(ordered);

        log.info(
                "AI fallback order: {}",
                registeredNames
        );
    }

    // Dùng khi chỉ cần kết quả dạng text
    public String generate(String prompt) {

        return generateAndValidate(
                prompt,
                response -> response
        );
    }

    // Dùng khi AI phải trả về dữ liệu hợp lệ
    public <T> T generateAndValidate(
            String prompt,
            Function<String, T> validator
    ) {
        Objects.requireNonNull(
                validator,
                "Validator không được null"
        );

        AiProviderException lastError = null;

        for (AiClient client : clients) {

            String providerName = client.getProviderName();

            try {
                log.info(
                        "Đang gọi AI provider: {}",
                        providerName
                );

                String response = client.generate(prompt);

                if (response == null || response.isBlank() || response.length() > 32000) {
                    throw new AiProviderException(
                            "AI trả về nội dung rỗng",
                            true,
                            null
                    );
                }

                T result;

                try {
                    result = validator.apply(response);

                    if (result == null) {
                        throw new IllegalArgumentException(
                                "Kết quả validation là null"
                        );
                    }

                } catch (RuntimeException e) {

                    log.warn(
                            "AI provider {} trả dữ liệu không hợp lệ: {}",
                            providerName,
                            e.getMessage()
                    );

                    lastError = new AiProviderException(
                            "AI trả về dữ liệu sai định dạng",
                            true,
                            e
                    );

                    continue;
                }

                log.info(
                        "AI provider {} phản hồi thành công",
                        providerName
                );

                return result;

            } catch (AiProviderException e) {

                lastError = e;

                log.warn(
                        "AI provider {} thất bại. Retryable: {}. Error: {}",
                        providerName,
                        e.isRetryable(),
                        e.getMessage()
                );

                // Continue to other providers even when this provider is misconfigured.

            }
        }

        throw new AiProviderException(
                "Tất cả AI provider đều không khả dụng hoặc trả dữ liệu không hợp lệ",
                false,
                lastError
        );
    }
}
