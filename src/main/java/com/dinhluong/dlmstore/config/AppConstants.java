package com.dinhluong.dlmstore.config;

public class AppConstants {
    public static final String[] USER_APIS = {
        "/api/users/**",
        "/api/cart/**",
        "/api/vouchers/**",
        "/api/orders/**",
        "/api/profile/**",
        "/api/addresses/**",
        "/api/reviews/"
    };
    public static final String[] ADMIN_APIS = {
            "/api/admin/orders/**",
            "/api/admin/payments/**",
            "/api/admin/vouchers/**",
            "/api/admin/users/**",
            "/api/admin/reviews/**",
            "/api/admin/categories/**",
            "/api/admin/**"
    };

    public static final String[] PUBLIC_APIS = {
            "/api/auth/**",
            "/api/public/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/oauth2/**",
            "/login/oauth2/**",
            "/ws/**",
            "api/products/**",
            "/api/chatbot",
            "api/reviews/products/**",
            "/admin/login",
            "/error",
    };
}
