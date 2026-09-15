package com.dinhluong.dlmstore.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.dinhluong.dlmstore.entity.Roles;
import com.dinhluong.dlmstore.entity.Users;
import com.dinhluong.dlmstore.exception.ValidationException;
import com.dinhluong.dlmstore.repository.RoleRepository;
import com.dinhluong.dlmstore.repository.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class GoogleService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    // Đã bỏ tiêm JwtTokenProvider ở đây

    @Transactional
    public Users googleLogin(String accessToken) { // Sửa kiểu trả về thành Users

        Map<String, Object> payload = getUserInfo(accessToken);

        String email = (String) payload.get("email");
        String fullName = (String) payload.get("name");
        String picture = (String) payload.get("picture");
        String googleId = (String) payload.get("sub");

        Users user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Nếu chưa có thì tạo mới
            Roles userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new ValidationException("ROLE USER not found"));

            user = new Users();
            user.setEmail(email);
            user.setFullName(fullName);
            user.setAvatarUrl(picture);
            user.setAuthProvider("GOOGLE");
            user.setProviderId(googleId);
            user.setRole(userRole);
            user.setIsEnabled(true);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());

            user = userRepository.save(user);
        } else {
            // 🔥 THÊM ĐOẠN CHECK NÀY ĐỂ CHẶN TÀI KHOẢN BỊ KHÓA
            if (!Boolean.TRUE.equals(user.getIsEnabled())) {
                throw new RuntimeException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ Admin!");
            }
        }

        // Thay vì sinh JWT và trả về LoginResponse, ta chỉ trả về User
        return user;
    }


    private Map<String, Object> getUserInfo(String accessToken) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            String url = "https://www.googleapis.com/oauth2/v3/userinfo";

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            return response.getBody();

        } catch (Exception e) {
            e.printStackTrace();
            throw new ValidationException("Invalid Google Access Token");
        }
    }
}