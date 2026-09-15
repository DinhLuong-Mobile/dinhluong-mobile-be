package com.dinhluong.dlmstore.controller.Admin;

import com.dinhluong.dlmstore.dto.ApiResponse;
import com.dinhluong.dlmstore.dto.requests.MasterDataRequest;
import com.dinhluong.dlmstore.dto.requests.SpecRequest;
import com.dinhluong.dlmstore.service.MasterDataService;
import com.dinhluong.dlmstore.service.SpecMasterService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminMasterDataController {

    private final MasterDataService masterDataService;
    private final SpecMasterService specMasterService; // Dùng constructor injection thay vì @Autowired để chuẩn SOLID

    // --- CATEGORY API ---
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<?>> getAllCategories() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách danh mục thành công", masterDataService.getAllCategories()));
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<?>> createCategory(@RequestBody MasterDataRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Thêm danh mục thành công", masterDataService.saveCategory(null, request)));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<?>> updateCategory(@PathVariable Long id, @RequestBody MasterDataRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật danh mục thành công", masterDataService.saveCategory(id, request)));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<String>> deleteCategory(@PathVariable Long id) {
        try {
            masterDataService.deleteCategory(id);
            return ResponseEntity.ok(ApiResponse.success("Xóa danh mục thành công!", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Không thể xóa danh mục này. Có thể đang được sử dụng trong Sản phẩm."));
        }
    }

    // --- BRAND API ---
    @GetMapping("/brands")
    public ResponseEntity<ApiResponse<?>> getAllBrands() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách thương hiệu thành công", masterDataService.getAllBrands()));
    }

    @PostMapping("/brands")
    public ResponseEntity<ApiResponse<?>> createBrand(@RequestBody MasterDataRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Thêm thương hiệu thành công", masterDataService.saveBrand(null, request)));
    }

    @PutMapping("/brands/{id}")
    public ResponseEntity<ApiResponse<?>> updateBrand(@PathVariable Long id, @RequestBody MasterDataRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thương hiệu thành công", masterDataService.saveBrand(id, request)));
    }

    @DeleteMapping("/brands/{id}")
    public ResponseEntity<ApiResponse<String>> deleteBrand(@PathVariable Long id) {
        try {
            masterDataService.deleteBrand(id);
            return ResponseEntity.ok(ApiResponse.success("Xóa thương hiệu thành công!", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Không thể xóa thương hiệu này. Có thể đang được sử dụng trong Sản phẩm."));
        }
    }

    // --- SPEC GROUP API ---
    @GetMapping("/spec-groups")
    public ResponseEntity<ApiResponse<?>> getAllSpecGroups() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách nhóm thông số thành công", specMasterService.getAllSpecGroups()));
    }

    @PostMapping("/spec-groups")
    public ResponseEntity<ApiResponse<?>> createSpecGroup(@RequestBody SpecRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Thêm nhóm thông số thành công", specMasterService.saveSpecGroup(null, request)));
    }

    @PutMapping("/spec-groups/{id}")
    public ResponseEntity<ApiResponse<?>> updateSpecGroup(@PathVariable Long id, @RequestBody SpecRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật nhóm thông số thành công", specMasterService.saveSpecGroup(id, request)));
    }

    @DeleteMapping("/spec-groups/{id}")
    public ResponseEntity<ApiResponse<String>> deleteSpecGroup(@PathVariable Long id) {
        try {
            specMasterService.deleteSpecGroup(id);
            return ResponseEntity.ok(ApiResponse.success("Xóa nhóm thông số thành công!", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Không thể xóa nhóm thông số này."));
        }
    }

    // --- SPEC ATTRIBUTE API ---
    @PostMapping("/spec-attributes")
    public ResponseEntity<ApiResponse<?>> createSpecAttribute(@RequestBody SpecRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Thêm thuộc tính thành công", specMasterService.saveSpecAttribute(null, request)));
    }

    @PutMapping("/spec-attributes/{id}")
    public ResponseEntity<ApiResponse<?>> updateSpecAttribute(@PathVariable Long id, @RequestBody SpecRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thuộc tính thành công", specMasterService.saveSpecAttribute(id, request)));
    }

    @DeleteMapping("/spec-attributes/{id}")
    public ResponseEntity<ApiResponse<String>> deleteSpecAttribute(@PathVariable Long id) {
        try {
            specMasterService.deleteSpecAttribute(id);
            return ResponseEntity.ok(ApiResponse.success("Xóa thuộc tính thành công!", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Không thể xóa thuộc tính này."));
        }
    }
}