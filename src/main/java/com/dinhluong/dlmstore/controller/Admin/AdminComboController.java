package com.dinhluong.dlmstore.controller.Admin;

import com.dinhluong.dlmstore.dto.ApiResponse;
import com.dinhluong.dlmstore.dto.requests.ProductComboRequest;
import com.dinhluong.dlmstore.service.AdminComboService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/product-combos")
@RequiredArgsConstructor
public class AdminComboController {

    private final AdminComboService adminComboService;

    // Lấy danh sách combo theo ID sản phẩm chính
    @GetMapping("/main/{mainProductId}")
    public ResponseEntity<ApiResponse<List<?>>> getCombosByMainProduct(@PathVariable Long mainProductId) {
        try {
            List<?> combos = adminComboService.getCombosByMainProduct(mainProductId);
            return ResponseEntity.ok(ApiResponse.success("Lấy danh sách combo thành công", combos));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // Thêm mới Combo
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createCombo(@RequestBody ProductComboRequest request) {
        try {
            adminComboService.createCombo(request);
            return ResponseEntity.ok(ApiResponse.success("Thêm combo thành công", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // Xóa Combo
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCombo(@PathVariable Long id) {
        try {
            adminComboService.deleteCombo(id);
            return ResponseEntity.ok(ApiResponse.success("Xóa combo thành công", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}