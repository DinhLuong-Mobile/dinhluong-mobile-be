
package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.ai.AiJsonParser;
import com.dinhluong.dlmstore.chatbot.ai.AiRouter;
import com.dinhluong.dlmstore.chatbot.dto.IntentConversationContext;
import com.dinhluong.dlmstore.chatbot.enums.AiModel;
import com.dinhluong.dlmstore.chatbot.enums.ChatIntent;
import com.dinhluong.dlmstore.chatbot.enums.ContextAction;
import com.dinhluong.dlmstore.chatbot.enums.MessageAction;
import com.dinhluong.dlmstore.chatbot.prompt.IntentPromptBuilder;
import com.dinhluong.dlmstore.dto.GeminiFilterCriteria;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IntentExtractionService {

    private static final Set<String> ALLOWED_CLEAR_FIELDS =
            Set.of(
                    "brands",
                    "osTypes",
                    "networks",
                    "minPrice",
                    "maxPrice",
                    "minBattery",
                    "minScreenSize",
                    "searchKeyword",
                    "ram",
                    "rom",
                    "targetProductName",
                    "compareProductNames"
                    , "categorySlug", "color", "chip", "camera", "inStockOnly", "usageGoal"
            );

    private final IntentPromptBuilder promptBuilder;
    private final AiJsonParser jsonParser;
    private final AiRouter aiRouter;

    /**
     * Phân tích intent không có ngữ cảnh.
     * Giữ tương thích với code cũ.
     */
    public GeminiFilterCriteria extract(
            String userMessage,
            AiModel model
    ) {
        return extract(userMessage, model, null);
    }

    /**
     * Phân tích intent có ngữ cảnh hội thoại.
     */
    public GeminiFilterCriteria extract(
            String userMessage,
            AiModel model,
            IntentConversationContext context
    ) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException(
                    "Nội dung tin nhắn không được rỗng"
            );
        }

        AiModel selectedModel =
                model == null ? AiModel.AUTO : model;

        String prompt = promptBuilder.build(
                userMessage,
                context
        );
        if (prompt.length() > 32000) {
            throw new com.dinhluong.dlmstore.chatbot.exception.AiProviderException("Conversation prompt budget exceeded", false, null);
        }

        return aiRouter.generateAndValidate(
                selectedModel,
                prompt,
                criteriaJson -> parseAndValidate(
                        criteriaJson,
                        context
                )
        );
    }

    /**
     * Parse JSON do AI trả về và kiểm tra dữ liệu.
     */
    private GeminiFilterCriteria parseAndValidate(
            String raw,
            IntentConversationContext context
    ) {
        GeminiFilterCriteria criteria =
                jsonParser.parse(
                        raw,
                        GeminiFilterCriteria.class
                );

        if (criteria == null) {
            throw new IllegalArgumentException(
                    "AI không trả về dữ liệu hợp lệ"
            );
        }

        validateIntent(criteria);

        normalizeDefaults(criteria);

        validatePrices(criteria);

        validateProductFilters(criteria);

        validateClearFields(criteria);

        validateContextAction(criteria, context);

        validateClarification(criteria, context);

        return criteria;
    }

    /**
     * Kiểm tra intent thuộc enum ChatIntent.
     */
    private void validateIntent(
            GeminiFilterCriteria criteria
    ) {
        String intent = criteria.getIntent();

        if (intent == null || intent.isBlank()) {
            throw new IllegalArgumentException(
                    "AI không trả về intent"
            );
        }

        String normalizedIntent =
                intent.trim().toUpperCase(java.util.Locale.ROOT);

        boolean validIntent =
                Arrays.stream(ChatIntent.values())
                        .anyMatch(value ->
                                value.name().equals(
                                        normalizedIntent
                                )
                        );

        if (!validIntent) {
            throw new IllegalArgumentException(
                    "Intent không hợp lệ: " + intent
            );
        }

        criteria.setIntent(normalizedIntent);
    }

    /**
     * Gán giá trị mặc định cho các trường mở rộng.
     */
    private void normalizeDefaults(
            GeminiFilterCriteria criteria
    ) {
        if (criteria.getReferenceType() == null) criteria.setReferenceType(com.dinhluong.dlmstore.chatbot.enums.ReferenceType.NONE);
        if (criteria.getFilterMode() == null) criteria.setFilterMode(com.dinhluong.dlmstore.chatbot.enums.FilterMode.MERGE);
        if (criteria.getContextAction() == null) {
            criteria.setContextAction(
                    ContextAction.CONTINUE
            );
        }

        if (criteria.getMessageAction() == null) {
            criteria.setMessageAction(
                    MessageAction.NONE
            );
        }

        if (criteria.getClearFields() == null) {
            criteria.setClearFields(List.of());
        }

        if (criteria.getCompareProductNames() == null) {
            criteria.setCompareProductNames(List.of());
        }

        if (criteria.getRequiresClarification() == null) {
            criteria.setRequiresClarification(false);
        }
    }

    /**
     * Kiểm tra giá sản phẩm.
     */
    private void validatePrices(
            GeminiFilterCriteria criteria
    ) {
        BigDecimal minPrice = criteria.getMinPrice();
        BigDecimal maxPrice = criteria.getMaxPrice();

        if (minPrice != null && minPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "minPrice không được âm"
            );
        }

        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "maxPrice không được âm"
            );
        }

        if (minPrice != null
                && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException(
                    "minPrice không được lớn hơn maxPrice"
            );
        }
    }

    /**
     * Kiểm tra các điều kiện sản phẩm.
     */
    private void validateProductFilters(
            GeminiFilterCriteria criteria
    ) {
        if (criteria.getResultLimit() != null && (criteria.getResultLimit() < 1 || criteria.getResultLimit() > 10)) {
            throw new IllegalArgumentException("resultLimit must be between 1 and 10");
        }
        if (criteria.getResultPage() != null && (criteria.getResultPage() < 0 || criteria.getResultPage() > 20)) {
            throw new IllegalArgumentException("resultPage must be between 0 and 20");
        }
        if (criteria.getComparisonVariants() != null) {
            if (criteria.getComparisonVariants().size() < 2 || criteria.getComparisonVariants().size() > 5) {
                throw new IllegalArgumentException("Compare between 2 and 5 variants");
            }
            for (var v : criteria.getComparisonVariants()) {
                if (v == null || v.productName() == null || v.productName().isBlank()) throw new IllegalArgumentException("Variant product name is required");
                for (String value : new String[]{v.productName(), v.ram(), v.rom(), v.color()}) validateString(value, "variant field");
            }
        }
        if (criteria.getProductPositions() != null && (criteria.getProductPositions().isEmpty()
                || criteria.getProductPositions().size() > 10
                || criteria.getProductPositions().stream().anyMatch(p -> p == null || p < 1 || p > 50)
                || new HashSet<>(criteria.getProductPositions()).size() != criteria.getProductPositions().size())) {
            throw new IllegalArgumentException("Invalid product positions");
        }
        for (String value : new String[]{criteria.getUsageGoal(), criteria.getTopicDescription(), criteria.getCategorySlug(),
                criteria.getChip(), criteria.getCamera(), criteria.getColor()}) validateString(value, "extended field");
        if (criteria.getOsTypes() != null) for (String os : criteria.getOsTypes()) {
            com.dinhluong.dlmstore.entity.Enums.OsType.valueOf(os.toUpperCase(java.util.Locale.ROOT));
        }
        if (criteria.getMinBattery() != null
                && criteria.getMinBattery() <= 0) {
            throw new IllegalArgumentException(
                    "minBattery không hợp lệ"
            );
        }

        if (criteria.getMinScreenSize() != null
                && (!Double.isFinite(criteria.getMinScreenSize())
                || criteria.getMinScreenSize() <= 0)) {
            throw new IllegalArgumentException(
                    "minScreenSize không hợp lệ"
            );
        }

        validateStringList(
                criteria.getBrands(),
                "brands"
        );

        validateStringList(
                criteria.getOsTypes(),
                "osTypes"
        );

        validateStringList(
                criteria.getNetworks(),
                "networks"
        );

        validateStringList(
                criteria.getCompareProductNames(),
                "compareProductNames"
        );

        validateString(
                criteria.getRam(),
                "ram"
        );

        validateString(
                criteria.getRom(),
                "rom"
        );

        validateString(
                criteria.getSearchKeyword(),
                "searchKeyword"
        );

        validateString(
                criteria.getTargetProductName(),
                "targetProductName"
        );

        validateString(
                criteria.getTopicName(),
                "topicName"
        );

        if (criteria.getTopicName() != null && criteria.getTopicName().length() > 150) {
            throw new IllegalArgumentException("topicName exceeds 150 characters");
        }
    }

    /**
     * Kiểm tra danh sách field cần xóa.
     */
    private void validateClearFields(
            GeminiFilterCriteria criteria
    ) {
        List<String> clearFields =
                criteria.getClearFields();

        Set<String> uniqueFields = new HashSet<>();

        for (String field : clearFields) {
            if (field == null
                    || !ALLOWED_CLEAR_FIELDS.contains(field)) {
                throw new IllegalArgumentException(
                        "Field cần xóa không hợp lệ: " + field
                );
            }

            if (!uniqueFields.add(field)) {
                throw new IllegalArgumentException(
                        "Field bị trùng trong clearFields: " + field
                );
            }
        }
    }

    /**
     * Kiểm tra hành động liên quan đến topic.
     */
    private void validateContextAction(
            GeminiFilterCriteria criteria,
            IntentConversationContext context
    ) {
        ContextAction action =
                criteria.getContextAction();

        if (action == ContextAction.SWITCH_TOPIC) {
            if (criteria.getTargetTopicId() == null && (criteria.getTopicDescription() != null
                    || criteria.getReferenceType() == com.dinhluong.dlmstore.chatbot.enums.ReferenceType.PREVIOUS_TOPIC)) return;
            Long targetTopicId =
                    criteria.getTargetTopicId();

            if (targetTopicId == null) {
                throw new IllegalArgumentException(
                        "SWITCH_TOPIC thiếu targetTopicId"
                );
            }

            if (context == null
                    || context.topics() == null) {
                throw new IllegalArgumentException(
                        "Không có danh sách topic để chuyển"
                );
            }

            boolean topicExists =
                    context.topics().stream()
                            .anyMatch(topic ->
                                    topic.id() != null
                                            && topic.id().equals(
                                            targetTopicId
                                    )
                            );

            if (!topicExists) {
                throw new IllegalArgumentException(
                        "targetTopicId không thuộc danh sách topic"
                );
            }
        }

        if (action == ContextAction.NEW_TOPIC
                && (criteria.getTopicName() == null
                || criteria.getTopicName().isBlank())) {

            // Cho phép backend tạo tên mặc định.
            criteria.setTopicName(
                    "Tư vấn điện thoại"
            );
        }
    }

    /**
     * Kiểm tra hành động liên quan đến clarification.
     */
    private void validateClarification(
            GeminiFilterCriteria criteria,
            IntentConversationContext context
    ) {
        boolean hasPending =
                context != null
                        && context.pendingQuestion() != null
                        && !context.pendingQuestion().isBlank();

        MessageAction action =
                criteria.getMessageAction();

        if (!hasPending
                && action == MessageAction.ANSWER_CLARIFICATION) {
            throw new IllegalArgumentException(
                    "Không có clarification đang chờ"
            );
        }

        if (Boolean.TRUE.equals(
                criteria.getRequiresClarification()
        )) {
            String question =
                    criteria.getClarificationQuestion();

            if (question == null || question.isBlank()) {
                throw new IllegalArgumentException(
                        "Thiếu clarificationQuestion"
                );
            }

            if (question.length() > 1000) {
                throw new IllegalArgumentException(
                        "clarificationQuestion quá dài"
                );
            }
        }
    }

    private void validateStringList(
            List<String> values,
            String fieldName
    ) {
        if (values == null) {
            return;
        }

        if (values.size() > 20) {
            throw new IllegalArgumentException(
                    fieldName + " vượt quá số lượng cho phép"
            );
        }

        for (String value : values) {
            if (value == null
                    || value.isBlank()
                    || value.length() > 200) {
                throw new IllegalArgumentException(
                        fieldName + " chứa giá trị không hợp lệ"
                );
            }
        }
    }

    private void validateString(
            String value,
            String fieldName
    ) {
        if (value != null && value.length() > 200) {
            throw new IllegalArgumentException(
                    fieldName + " quá dài"
            );
        }
    }
}
