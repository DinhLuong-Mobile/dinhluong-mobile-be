
package com.dinhluong.dlmstore.chatbot.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class GuestSessionService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    public String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "Guest token không hợp lệ"
            );
        }

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "Không thể hash guest token",
                    e
            );
        }
    }

    public boolean matches(
            String rawToken,
            String storedHash
    ) {
        if (rawToken == null
                || rawToken.isBlank()
                || storedHash == null
                || storedHash.isBlank()) {
            return false;
        }

        byte[] actual = hashToken(rawToken)
                .getBytes(StandardCharsets.US_ASCII);

        byte[] expected = storedHash
                .getBytes(StandardCharsets.US_ASCII);

        return MessageDigest.isEqual(actual, expected);
    }
}
