package com.dinhluong.dlmstore.config;

import com.dinhluong.dlmstore.chatbot.controller.ChatbotController;
import com.dinhluong.dlmstore.chatbot.service.ChatbotService;
import com.dinhluong.dlmstore.chatbot.service.GuestSessionService;
import com.dinhluong.dlmstore.controller.CartController;
import com.dinhluong.dlmstore.controller.NotificationController;
import com.dinhluong.dlmstore.controller.ProductController;
import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;
import com.dinhluong.dlmstore.security.*;
import com.dinhluong.dlmstore.service.*;
import com.dinhluong.dlmstore.utils.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real HTTP/Tomcat coverage; business collaborators are mocked and no database is started. */
@SpringBootTest(classes = CorsHttpIntegrationTest.HttpApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsHttpIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({WebConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class,
            JwtAuthenticationEntryPoint.class, GuestSessionService.class, CartController.class,
            ProductController.class, NotificationController.class, ChatbotController.class})
    static class HttpApplication { }

    private static final String FRONTEND = "https://localhost:5173";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort int port;
    @MockBean CartService cart;
    @MockBean ProductService products;
    @MockBean ProductComboService combos;
    @MockBean NotificationService notifications;
    @MockBean ChatbotService chatbot;
    @MockBean JwtTokenProvider jwt;
    @MockBean CustomUserDetailsService users;

    private HttpRequest.Builder request(String path, String origin) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Origin", origin);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertAllowed(HttpResponse<?> response, int status) {
        assertEquals(status, response.statusCode());
        assertEquals(FRONTEND, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals("true", response.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({"/api/cart/15,GET", "/api/products/featured,GET", "/api/notifications,GET",
            "/api/notifications/unread-count,GET", "/api/chatbot,POST"})
    void realHttpPreflightBeforeAuthentication(String path, String method) throws Exception {
        var response = send(request(path, FRONTEND).method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", "authorization,content-type"));
        assertAllowed(response, 200);
        assertEquals("authorization, content-type",
                response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow());
        verifyNoInteractions(jwt, users, cart, products, notifications, chatbot);
    }

    @Test
    void realHttpApiRequestsRetainJwtAndGuestSemantics() throws Exception {
        when(jwt.validateToken("test-token")).thenReturn(true);
        when(jwt.getEmailFromJWT("test-token")).thenReturn("test@example.test");
        when(users.loadUserByUsername("test@example.test")).thenReturn(new CustomUserPrincipal(
                15L, "test@example.test", "", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        for (String path : List.of("/api/cart/15", "/api/notifications", "/api/notifications/unread-count")) {
            assertAllowed(send(request(path, FRONTEND).GET()), 401);
            assertAllowed(send(request(path, FRONTEND).header("Authorization", "Bearer test-token").GET()), 200);
        }
        assertAllowed(send(request("/api/products/featured", FRONTEND).GET()), 200);
        when(chatbot.processUserMessage(any(), any(), anyString(), any()))
                .thenReturn(new ChatBotResponse("hello", List.of()));
        var guestResponse = send(request("/api/chatbot", FRONTEND)
                .header("Content-Type", "application/json")
                .header("Cookie", "dlm_guest=existing-guest-token")
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"hi\"}")));
        assertAllowed(guestResponse, 200);
        assertTrue(guestResponse.headers().allValues("Set-Cookie").isEmpty());
        assertAllowed(send(request("/api/chatbot", FRONTEND)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer invalid")
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"hi\"}"))), 401);
    }

    @Test
    void realHttpUnknownOriginRemainsBlocked() throws Exception {
        for (String path : List.of("/api/cart/15", "/api/products/featured", "/api/notifications",
                "/api/notifications/unread-count", "/api/chatbot")) {
            var response = send(request(path, "https://untrusted.example")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .header("Access-Control-Request-Method", path.endsWith("chatbot") ? "POST" : "GET"));
            assertEquals(403, response.statusCode());
            assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        }
    }
}
