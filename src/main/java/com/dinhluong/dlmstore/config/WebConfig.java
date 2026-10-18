    package com.dinhluong.dlmstore.config;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.web.servlet.config.annotation.CorsRegistry;
    import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

    @Configuration
    public class WebConfig {

        // Local development defaults; deployments can replace the entire allowlist.
        @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,https://localhost:5173}")
        private String[] allowedOrigins;

        @Bean
        public WebMvcConfigurer corsConfigurer() {
            return new WebMvcConfigurer() {
                @Override
                public void addCorsMappings(CorsRegistry registry) {
                    registry.addMapping("/**")
                            .allowedOrigins(allowedOrigins)
                            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                            .allowedHeaders("*")
                            .allowCredentials(true); 
                }
            };
        }
    }
