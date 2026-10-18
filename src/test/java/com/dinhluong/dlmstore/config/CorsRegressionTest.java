package com.dinhluong.dlmstore.config;

import com.dinhluong.dlmstore.chatbot.controller.ChatbotController;
import com.dinhluong.dlmstore.chatbot.dto.ConversationPrincipal;
import com.dinhluong.dlmstore.chatbot.service.ChatbotService;
import com.dinhluong.dlmstore.chatbot.service.GuestSessionService;
import com.dinhluong.dlmstore.controller.CartController;
import com.dinhluong.dlmstore.controller.NotificationController;
import com.dinhluong.dlmstore.controller.ProductController;
import com.dinhluong.dlmstore.dto.responses.ChatBotResponse;
import com.dinhluong.dlmstore.security.CustomUserDetailsService;
import com.dinhluong.dlmstore.security.CustomUserPrincipal;
import com.dinhluong.dlmstore.security.JwtAuthenticationEntryPoint;
import com.dinhluong.dlmstore.security.JwtAuthenticationFilter;
import com.dinhluong.dlmstore.service.CartService;
import com.dinhluong.dlmstore.service.NotificationService;
import com.dinhluong.dlmstore.service.ProductComboService;
import com.dinhluong.dlmstore.service.ProductService;
import com.dinhluong.dlmstore.utils.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the real MVC CORS configuration, security chain, JWT filter and affected controllers. */
@WebMvcTest(controllers = {CartController.class, ProductController.class,
        NotificationController.class, ChatbotController.class})
@Import({WebConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, GuestSessionService.class})
class CorsRegressionTest {
    private static final String FRONTEND = "https://localhost:5173";

    @Autowired MockMvc mvc;
    @MockBean CartService cartService;
    @MockBean ProductService productService;
    @MockBean ProductComboService comboService;
    @MockBean NotificationService notificationService;
    @MockBean ChatbotService chatbotService;
    @MockBean JwtTokenProvider jwt;
    @MockBean CustomUserDetailsService users;

    @ParameterizedTest
    @CsvSource({"/api/cart/15,GET", "/api/products/featured,GET", "/api/notifications,GET",
            "/api/notifications/unread-count,GET", "/api/chatbot,POST"})
    void httpsPreflightSucceedsWithoutAuthentication(String path, String method) throws Exception {
        mvc.perform(options(path).servletPath(path)
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "authorization, content-type"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString(method)));
        // Preflight is consumed by CORS before JWT validation and controller invocation.
        verifyNoInteractions(jwt, users, cartService, productService, notificationService, chatbotService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/cart/15", "/api/products/featured", "/api/notifications",
            "/api/notifications/unread-count", "/api/chatbot"})
    void unknownOriginIsRejected(String path) throws Exception {
        mvc.perform(options(path).header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, path.endsWith("chatbot") ? "POST" : "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        verifyNoInteractions(jwt, users, cartService, productService, notificationService, chatbotService);
    }

    @Test
    void existingHttpLocalhostOriginRemainsAllowed() throws Exception {
        mvc.perform(options("/api/chatbot").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/cart/15", "/api/notifications", "/api/notifications/unread-count"})
    void protectedRequestsStillRequireJwtAndExpose401ToAllowedFrontend(String path) throws Exception {
        mvc.perform(get(path).header(HttpHeaders.ORIGIN, FRONTEND))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/cart/15", "/api/notifications", "/api/notifications/unread-count"})
    void protectedRequestsAcceptJwtAfterCors(String path) throws Exception {
        when(jwt.validateToken("test-token")).thenReturn(true);
        when(jwt.getEmailFromJWT("test-token")).thenReturn("test@example.test");
        when(users.loadUserByUsername("test@example.test")).thenReturn(new CustomUserPrincipal(
                15L, "test@example.test", "", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        mvc.perform(get(path).header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        if (path.startsWith("/api/cart")) verify(cartService).getCartByUserId(15L);
        else if (path.endsWith("unread-count")) verify(notificationService).getUnreadCount(15L);
        else verify(notificationService).getUserNotifications(15L);
    }

    @Test
    void productsRemainPublicWithCorsHeaders() throws Exception {
        mvc.perform(get("/api/products/featured").header(HttpHeaders.ORIGIN, FRONTEND))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND));
        verify(productService).getFeaturedProducts(10);
    }

    @Test
    void guestChatAcceptsJsonAndReusesHttpOnlyCookieIdentity() throws Exception {
        when(chatbotService.processUserMessage(any(), any(), anyString(), any()))
                .thenReturn(new ChatBotResponse("hello", List.of()));
        mvc.perform(post("/api/chatbot").servletPath("/api/chatbot")
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .cookie(new Cookie("dlm_guest", "existing-guest-token"))
                        .contentType("application/json")
                        .content("{\"message\":\"hi\",\"conversationId\":\"existing-conversation\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        verify(chatbotService).processUserMessage(eq(new ConversationPrincipal(null, "existing-guest-token")),
                eq("existing-conversation"), eq("hi"), any());
    }

    @Test
    void invalidChatbotJwtStillReturns401WithCorsHeaders() throws Exception {
        mvc.perform(post("/api/chatbot").servletPath("/api/chatbot")
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                        .contentType("application/json").content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND));
        verifyNoInteractions(chatbotService);
    }

    @Test
    void unknownOriginActualRequestIsAlsoRejected() throws Exception {
        mvc.perform(get("/api/products/featured").header(HttpHeaders.ORIGIN, "https://untrusted.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        verifyNoInteractions(productService);
    }
}
