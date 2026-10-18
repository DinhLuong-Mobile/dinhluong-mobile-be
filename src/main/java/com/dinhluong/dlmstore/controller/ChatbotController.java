//package com.dinhluong.dlmstore.controller;
//
//import com.dinhluong.dlmstore.dto.ApiResponse;
//import com.dinhluong.dlmstore.dto.requests.ChatBotRequest;
//import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;
//import com.dinhluong.dlmstore.entity.Users;
//import com.dinhluong.dlmstore.service.ChatbotServiceLegacy;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.annotation.AuthenticationPrincipal;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/chatbot")
//@RequiredArgsConstructor
//public class ChatbotController {
//
//    private final ChatbotServiceLegacy chatbotService;
//
//    @PostMapping
//    public ResponseEntity<ApiResponse<ChatBotResponse>> chat(
//            @RequestBody ChatBotRequest request,
//            @AuthenticationPrincipal Users currentUser // Lấy thẳng user đang đăng nhập từ Spring Security
//    ) {
//        // 1. Validate nội dung tin nhắn
//        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
//            return ResponseEntity.badRequest()
//                    .body(ApiResponse.error(400, "Dạ, em sẵn sàng hỗ trợ, anh/chị cần hỏi gì ạ?"));
//        }
//
//        try {
//            // 2. Lấy ID từ đối tượng Authentication (nếu khách chưa login thì currentUser sẽ null)
//            Long userId = (currentUser != null) ? currentUser.getId() : null;
//
//            // 3. Gọi Service xử lý (Lúc này Service sẽ lưu đúng ID người dùng vào DB)
//            ChatBotResponse answer = chatbotService.processUserMessage(userId, request.getMessage());
//
//            return ResponseEntity.ok(ApiResponse.success("AI phản hồi thành công", answer));
//
//        } catch (Exception e) {
//            return ResponseEntity.ok(ApiResponse.success("AI busy",
//                    new ChatBotResponse("Dạ, hệ thống đang bận một chút, anh/chị thử lại sau vài giây nhé!", null)));
//        }
//    }
//}