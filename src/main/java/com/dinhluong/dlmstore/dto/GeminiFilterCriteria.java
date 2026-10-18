package com.dinhluong.dlmstore.dto;

import java.math.BigDecimal;
import java.util.List;

import com.dinhluong.dlmstore.chatbot.enums.ContextAction;
import com.dinhluong.dlmstore.chatbot.enums.MessageAction;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiFilterCriteria {
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<Long> pendingSelectedIds;
    private List<com.dinhluong.dlmstore.chatbot.dto.VariantRequest> comparisonVariants;
    private com.dinhluong.dlmstore.chatbot.enums.ReferenceType referenceType;
    private com.dinhluong.dlmstore.chatbot.enums.RankingCriterion rankingCriterion;
    private com.dinhluong.dlmstore.chatbot.enums.FilterMode filterMode;
    private List<Integer> productPositions;
    private Integer resultLimit;
    private Integer resultPage;
    private String topicDescription;
    private String usageGoal;
    private String categorySlug;
    private String color;
    private String chip;
    private String camera;
    private Boolean inStockOnly;
    // Intent của người dùng: Thêm "OUT_OF_DOMAIN"
    private String intent; // "SEARCH", "DETAIL", "COMPARE", "CHAT", "OUT_OF_DOMAIN"

    // Các tiêu chí lọc (cho intent SEARCH)
    private List<String> brands;
    private List<String> osTypes;
    private List<String> networks;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer minBattery;
    private Double minScreenSize;
    private String searchKeyword;
    
    // THÊM 2 TRƯỜNG NÀY ĐỂ BẮT CẤU HÌNH SÂU
    private String ram; // VD: "8GB"
    private String rom; // VD: "256GB"

    // Các tiêu chí cụ thể (cho intent DETAIL / COMPARE)
    private String targetProductName;
    private List<String> compareProductNames; // Giữ nguyên List của bạn vì nó rất chuẩn
    private ContextAction contextAction;

    private MessageAction messageAction;

    private Long targetTopicId;

    private String topicName;

    private List<String> clearFields;

    private Boolean requiresClarification;

    private String clarificationQuestion;
}
