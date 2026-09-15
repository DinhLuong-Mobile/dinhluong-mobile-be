package com.dinhluong.dlmstore.dto.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenRefreshRequest {
    @NotBlank(message = "Refresh Token không được để trống")
    private String refreshToken;
}