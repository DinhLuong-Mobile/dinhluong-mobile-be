
package com.dinhluong.dlmstore.chatbot.controller;

import com.dinhluong.dlmstore.chatbot.service.ChatbotService;
import com.dinhluong.dlmstore.dto.ApiResponse;
import com.dinhluong.dlmstore.dto.requests.ChatBotRequest;
import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;
import com.dinhluong.dlmstore.entity.Users;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping
    public ResponseEntity<ApiResponse<ChatBotResponse>> chat(
            @RequestBody ChatBotRequest request,
            @AuthenticationPrincipal Users currentUser
    ) {

        if (request == null
                || request.getMessage() == null
                || request.getMessage().isBlank()) {

            throw new IllegalArgumentException(
                    "Nội dung tin nhắn không được để trống"
            );
        }

        Long userId = currentUser != null
                ? currentUser.getId()
                : null;

        ChatBotResponse response =
                chatbotService.processUserMessage(
                        userId,
                        request.getMessage(),
                        request.getModel()
                );

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Chatbot phản hồi thành công",
                        response
                )
        );
    }
}
