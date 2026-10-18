
package com.dinhluong.dlmstore.chatbot.controller;

import com.dinhluong.dlmstore.chatbot.dto.ConversationPrincipal;
import com.dinhluong.dlmstore.chatbot.service.ChatbotService;
import com.dinhluong.dlmstore.chatbot.service.GuestSessionService;
import com.dinhluong.dlmstore.dto.requests.ChatBotRequest;
import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;
import com.dinhluong.dlmstore.entity.Users;
import com.dinhluong.dlmstore.dto.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    @org.springframework.beans.factory.annotation.Value("${chatbot.cookie.secure:false}")
    private boolean secureCookie;

    private static final String GUEST_COOKIE = "dlm_guest";

    private final ChatbotService chatbotService;
    private final GuestSessionService guestSessionService;

    @PostMapping
    public ResponseEntity<ApiResponse<ChatBotResponse>> chat(
            @RequestBody ChatBotRequest request,
            @AuthenticationPrincipal Object authenticatedPrincipal,
            @CookieValue(
                    value = GUEST_COOKIE,
                    required = false
            ) String guestCookie,
            HttpServletResponse servletResponse,
            jakarta.servlet.http.HttpServletRequest servletRequest
    ) {
        if (request.getMessage() == null
                || request.getMessage().isBlank()) {
            throw new IllegalArgumentException(
                    "Nội dung tin nhắn không được để trống"
            );
        }

        Long userId = resolveUserId(authenticatedPrincipal);

        String guestToken = null;

        if (userId == null) {

            guestToken = guestCookie;

            if (guestToken == null || guestToken.isBlank()) {

                guestToken = guestSessionService.generateToken();

                ResponseCookie cookie = ResponseCookie
                        .from(GUEST_COOKIE, guestToken)
                        .httpOnly(true)
                        .secure(secureCookie || servletRequest.isSecure())
                        .sameSite("Lax")
                        .path("/")
                        .maxAge(Duration.ofDays(7))
                        .build();

                servletResponse.addHeader(
                        HttpHeaders.SET_COOKIE,
                        cookie.toString()
                );
            }
        }

        ConversationPrincipal principal =
                new ConversationPrincipal(
                        userId,
                        guestToken
                );

        ChatBotResponse response = request.getSelectedProductId() != null || request.getClarificationId() != null
                ? chatbotService.processUserMessage(principal, request.getConversationId(), request.getMessage(),
                        request.getModel(), request.getSelectedProductId(), request.getClarificationId())
                : chatbotService.processUserMessage(
                        principal,
                        request.getConversationId(),
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

    private Long resolveUserId(Object principal) {

        if (principal instanceof com.dinhluong.dlmstore.security.CustomUserPrincipal user) {
            return user.getId();
        }

        if (principal instanceof Users user) {
            return user.getId();
        }

        // Principal là String/UserDetails thì phải
        // tra userId qua UserService hoặc JWT claim.
        // Không tự tin cậy userId do frontend gửi.
        return null;
    }
}
