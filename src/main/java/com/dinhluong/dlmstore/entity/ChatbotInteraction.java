//package com.dinhluong.dlmstore.entity;
//
//import jakarta.persistence.*;
//import lombok.*;
//import java.time.LocalDateTime;
//
//@Entity
//@Table(name = "chatbot_interactions")
//@Data
//@NoArgsConstructor
//@AllArgsConstructor
//@Builder
//public class ChatbotInteraction {
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;
//
//    @Column(name = "user_id")
//    private Long userId;
//
//    @Column(columnDefinition = "TEXT")
//    private String query;
//
//    @Column(columnDefinition = "TEXT")
//    private String response;
//
//    @Column(name = "created_at", updatable = false)
//    private LocalDateTime createdAt;
//
//    @PrePersist
//    protected void onCreate() {
//        this.createdAt = LocalDateTime.now();
//    }
//}