//package com.dinhluong.dlmstore.repository;
//
//import java.time.LocalDateTime;
//
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//
//import com.dinhluong.dlmstore.chatbot.entity.ChatbotInteraction;
//
//@Repository
//public interface ChatbotInteractionRepository extends JpaRepository<ChatbotInteraction, Long> {
//    @Query("SELECT COUNT(c) FROM ChatbotInteraction c WHERE c.createdAt >= :startDate AND c.createdAt <= :endDate")
//    long countInteractions(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
//}
