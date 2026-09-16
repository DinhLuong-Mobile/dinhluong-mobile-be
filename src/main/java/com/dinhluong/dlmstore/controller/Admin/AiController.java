package com.dinhluong.dlmstore.controller.Admin;

import com.dinhluong.dlmstore.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dinhluong.dlmstore.dto.requests.AiAccessoryRequest;
import com.dinhluong.dlmstore.dto.requests.AiContentRequest;
import com.dinhluong.dlmstore.dto.requests.AiSpecExtractRequest;
import com.dinhluong.dlmstore.service.tools.GeminiAiService;

@RestController
@RequestMapping("/api/admin/ai")
public class AiController {

    @Autowired
    private GeminiAiService geminiAiService;

    @PostMapping("/generate-description")
    public ResponseEntity<?> generateDescription(@RequestBody AiContentRequest request) {
        String result = geminiAiService.generateProductDescription(request);
        return ResponseEntity.ok(ApiResponse.success("Viet bai mo ta thanh cong",result));
    }

    @PostMapping("/extract-specs")
    public ResponseEntity<?> extractSpecs(@RequestBody AiSpecExtractRequest request) {
        // Rào chắn bảo vệ: Không có chữ thì báo lỗi luôn
        if (request.getRawText() == null || request.getRawText().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Vui lòng cung cấp đoạn văn bản thông số.");
        }
        
        try {
            // Gọi Service và trả về trực tiếp chuỗi JSON cho Front-end
            String generatedJson = geminiAiService.extractSpecsFromText(request);
            return ResponseEntity.ok(ApiResponse.success("Boc tach thanh cong",generatedJson));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Lỗi hệ thống AI: " + e.getMessage());
        }
    }


    @PostMapping("accessory/generate-description")
    public ResponseEntity<?> generateDescription(@RequestBody AiAccessoryRequest request) {
        String htmlContent = geminiAiService.generateAccessoryDescription(request);
        return ResponseEntity.ok(ApiResponse.success("Viet bai mo ta thanh cong",htmlContent));
    }

    // 2. Bóc tách thông số phụ kiện từ text thô
    @PostMapping("accessory/extract-specs")
    public ResponseEntity<?> extractSpecs(@RequestBody AiAccessoryRequest request) {
        String jsonResult = geminiAiService.extractAccessorySpecsJson(request.getRawText());
        return ResponseEntity.ok(ApiResponse.success("Boc tach thanh cong",jsonResult));
    }

}