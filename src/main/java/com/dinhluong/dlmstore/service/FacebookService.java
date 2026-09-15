package com.dinhluong.dlmstore.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.dinhluong.dlmstore.entity.Roles;
import com.dinhluong.dlmstore.entity.Users;
import com.dinhluong.dlmstore.exception.ValidationException;
import com.dinhluong.dlmstore.repository.RoleRepository;
import com.dinhluong.dlmstore.repository.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class FacebookService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    // Đã bỏ tiêm JwtTokenProvider ở đây

    @Transactional
    public Users facebookLogin(String accessToken) { // Sửa kiểu trả về thành Users

        Map<String, Object> payload = getFacebookUserInfo(accessToken);

        String facebookId = (String) payload.get("id");
        String name = (String) payload.get("name");
        String email = (String) payload.get("email");

        String picture = null;
        if (payload.containsKey("picture")) {
            Map<String, Object> pictureObj = (Map<String, Object>) payload.get("picture");
            Map<String, Object> dataObj = (Map<String, Object>) pictureObj.get("data");
            picture = (String) dataObj.get("url");
        }

        if (email == null) {
            email = facebookId + "@facebook.com";
        }

        final String finalEmail = email;
        final String finalPicture = picture;

        // TÌM HOẶC TẠO MỚI USER
        Users user = userRepository.findByEmail(finalEmail).orElse(null);

        if (user == null) {
            Roles userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new ValidationException("ROLE USER not found"));

            user = new Users();
            user.setEmail(finalEmail);
            user.setFullName(name);
            user.setAvatarUrl(finalPicture);
            user.setAuthProvider("FACEBOOK");
            user.setProviderId(facebookId);
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

    private Map<String, Object> getFacebookUserInfo(String accessToken) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://graph.facebook.com/me?fields=id,name,email,picture&access_token=" + accessToken;
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            throw new ValidationException("Invalid Facebook Access Token");
        }
    }
}