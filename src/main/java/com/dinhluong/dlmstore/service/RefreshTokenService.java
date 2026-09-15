package com.dinhluong.dlmstore.service;

import com.dinhluong.dlmstore.entity.RefreshToken;
import com.dinhluong.dlmstore.entity.Users;
import com.dinhluong.dlmstore.repository.RefreshTokenRepository;
import com.dinhluong.dlmstore.repository.UserRepository; // Đảm bảo đúng tên repo Users của bạn
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    // Bạn có thể cấu hình thời gian sống trong file application.properties (ví dụ: 604800000 = 7 ngày)
    // Nếu không cấu hình, mặc định sẽ lấy 7 ngày (tính bằng mili giây)
    @Value("${dlmstore.app.jwtRefreshExpirationMs:604800000}")
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository usersRepository;

    /**
     * Tạo mới hoặc cập nhật Refresh Token cho User khi đăng nhập thành công
     */
    @Transactional
    public RefreshToken createRefreshToken(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        // Xóa token cũ của user này trước (Mỗi user chỉ giữ 1 token hoạt động mới nhất)
        refreshTokenRepository.deleteByUser(user);

        // Tạo token mới
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString()) // Sinh chuỗi ngẫu nhiên không trùng lặp
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Tìm kiếm token trong database
     */
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Kiểm tra xem token đã hết hạn chưa. Nếu hết hạn thì xóa luôn trong DB.
     */
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }
    @Transactional
    public int deleteByUserId(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        return refreshTokenRepository.deleteByUser(user);
    }
}