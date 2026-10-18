package com.dinhluong.dlmstore.chatbot.exception;

import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice(basePackages = "com.dinhluong.dlmstore.chatbot.controller")
public class ChatbotExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ChatBotResponse> handleBadRequest(
            IllegalArgumentException e
    ) {

        return ResponseEntity.badRequest().body(
                new ChatBotResponse(
                        e.getMessage(),
                        List.of()
                )
        );
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ChatBotResponse> handleAiError(
            AiProviderException e
    ) {

        log.error("Tất cả AI provider thất bại", e);

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ChatBotResponse(
                        "Dạ hệ thống tư vấn AI đang tạm thời bận. "
                                + "Anh/chị vui lòng thử lại sau ạ.",
                        List.of()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ChatBotResponse> handleUnexpectedError(
            Exception e
    ) {

        log.error("Lỗi không mong muốn trong chatbot", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ChatBotResponse(
                        "Hệ thống đang gặp sự cố. "
                                + "Anh/chị vui lòng thử lại sau ạ.",
                        List.of()
                ));
    }
}