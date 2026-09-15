package com.dinhluong.dlmstore.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import com.dinhluong.dlmstore.entity.Users;

import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {
    @Value("${jwt.secret}")
    private String JWT_SECRET ;
    private final long JWT_EXPIRATION = 1000 * 60 * 60 * 24;
   // private final long JWT_EXPIRATION = 1000 * 30;

    private  Key key ;
    @PostConstruct
    public void init() {
        if (JWT_SECRET == null || JWT_SECRET.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters long!");
        }
        this.key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
    }
    public String generateToken(Users user) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + JWT_EXPIRATION);

    return Jwts.builder()
            .setSubject(user.getEmail())
            .claim("id", user.getId())                
            .claim("role", user.getRole().getName())      
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
}

    public Long getUserIdFromJWT(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("id", Long.class);
    }

    public String getEmailFromJWT(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
