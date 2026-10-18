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

        // Provider exception causes can contain response bodies or credential-bearing URLs.
        log.warn("AI provider unavailable: {}", e.getClass().getSimpleName());

        ChatBotResponse response = new ChatBotResponse(
                "Dạ hệ thống tư vấn AI đang tạm thời bận. Anh/chị vui lòng thử lại sau ạ.", List.of());
        if (e instanceof ConversationAiException conversationError) {
            response.setConversationId(conversationError.getConversationId());
            response.setTopicId(conversationError.getTopicId());
        }

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ChatBotResponse> handleForbidden(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ChatBotResponse("Bạn không có quyền truy cập hội thoại", List.of()));
    }

    @ExceptionHandler(ConversationStateException.class)
    public ResponseEntity<ChatBotResponse> handleConflict(ConversationStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ChatBotResponse("Phiên hội thoại hoặc câu hỏi không còn khả dụng", List.of()));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ChatBotResponse> handleInvalidJson(Exception e) {
        return ResponseEntity.badRequest().body(new ChatBotResponse("Request JSON không hợp lệ", List.of()));
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ChatBotResponse> handleDatabaseError(org.springframework.dao.DataAccessException e) {
        log.error("Lỗi truy cập dữ liệu hội thoại", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ChatBotResponse(
                "Không thể truy cập dữ liệu hội thoại. Anh/chị vui lòng thử lại sau ạ.", List.of()));
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
