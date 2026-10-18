
package com.dinhluong.dlmstore.dto.responses;

import com.dinhluong.dlmstore.dto.ChatProductDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatBotResponse {

    private String answer;

    private List<ChatProductDto> products;

    private String conversationId;

    private Long topicId;

    private Long clarificationId;

    private boolean requiresClarification;

    // Giữ tương thích với code cũ
    public ChatBotResponse(
            String answer,
            List<ChatProductDto> products
    ) {
        this.answer = answer;
        this.products = products;
    }
}
