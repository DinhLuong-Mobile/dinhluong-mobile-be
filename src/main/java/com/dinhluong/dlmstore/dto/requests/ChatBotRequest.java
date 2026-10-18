
package com.dinhluong.dlmstore.dto.requests;

import com.dinhluong.dlmstore.chatbot.enums.AiModel;
import lombok.Data;

@Data
public class ChatBotRequest {

    private String message;
    private Long selectedProductId;
    private Long clarificationId;

    private AiModel model = AiModel.AUTO;

    // Null khi bắt đầu cuộc trò chuyện mới
    private String conversationId;
}
