package com.dinhluong.dlmstore.dto.requests;

import com.dinhluong.dlmstore.chatbot.enums.AiModel;
import lombok.Data;

@Data
public class ChatBotRequest {
    private String message;
    private AiModel model = AiModel.AUTO;
}
