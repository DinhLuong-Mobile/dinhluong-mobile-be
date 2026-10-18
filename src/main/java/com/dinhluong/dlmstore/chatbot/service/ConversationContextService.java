
package com.dinhluong.dlmstore.chatbot.service;

import com.dinhluong.dlmstore.chatbot.dto.ConversationContextUpdate;
import com.dinhluong.dlmstore.chatbot.dto.ConversationPrincipal;
import com.dinhluong.dlmstore.chatbot.entity.ChatbotTopic;
import com.dinhluong.dlmstore.chatbot.repository.ChatbotTopicRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConversationContextService {

    private static final String LAST_PRODUCT_IDS = "lastProductIds";

    private static final Set<String> ALLOWED_FIELDS = Set.of(
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
            "compareProductNames",
            LAST_PRODUCT_IDS
    );

    private static final Set<String> LIST_FIELDS = Set.of(
            "brands",
            "osTypes",
            "networks",
            "compareProductNames"
    );

    private static final Set<String> NUMERIC_FIELDS = Set.of(
            "minPrice",
            "maxPrice",
            "minBattery",
            "minScreenSize"
    );

    private final ChatbotTopicRepository topicRepository;
    private final ConversationSessionService sessionService;

    @Transactional
    public Map<String, Object> getContext(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId
    ) {
        ChatbotTopic topic = getAuthorizedTopic(
                conversationId,
                principal,
                topicId
        );

        return copyContext(topic.getContextData());
    }

    @Transactional
    public Map<String, Object> updateContext(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId,
            ConversationContextUpdate update
    ) {
        Objects.requireNonNull(
                update,
                "Context update không được null"
        );

        ChatbotTopic topic = getAuthorizedTopic(
                conversationId,
                principal,
                topicId
        );

        Map<String, Object> context =
                copyContext(topic.getContextData());

        // 1. Xóa điều kiện cũ
        if (update.clearFields() != null) {
            for (String field : update.clearFields()) {
                validateField(field);

                if (LAST_PRODUCT_IDS.equals(field)) {
                    throw new IllegalArgumentException(
                            "Không thể xóa lastProductIds qua clearFields"
                    );
                }

                context.remove(field);
            }
        }

        // 2. Thêm hoặc cập nhật điều kiện
        if (update.setFields() != null) {
            update.setFields().forEach((field, value) -> {
                validateField(field);

                if (LAST_PRODUCT_IDS.equals(field)) {
                    throw new IllegalArgumentException(
                            "Phải cập nhật sản phẩm qua presentedProductIds"
                    );
                }

                if (value == null) {
                    context.remove(field);
                } else {
                    validateValue(field, value);
                    context.put(field, value);
                }
            });
        }

        // 3. Cập nhật danh sách sản phẩm vừa giới thiệu
        if (update.presentedProductIds() != null) {

            List<Long> ids = update.presentedProductIds();

            if (ids.size() > 50
                    || ids.stream().anyMatch(
                    id -> id == null || id <= 0
            )) {
                throw new IllegalArgumentException(
                        "Danh sách sản phẩm không hợp lệ"
                );
            }

            context.put(
                    LAST_PRODUCT_IDS,
                    new ArrayList<>(new LinkedHashSet<>(ids))
            );
        }

        // Kiểm tra khoảng giá sau khi merge
        validatePriceRange(context);

        // Gán Map mới để Hibernate nhận biết JSON thay đổi
        topic.setContextData(context);

        return copyContext(context);
    }

    @Transactional
    public List<Long> getLastPresentedProductIds(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId
    ) {
        Map<String, Object> context = getContext(
                conversationId,
                principal,
                topicId
        );

        Object raw = context.get(LAST_PRODUCT_IDS);

        if (!(raw instanceof List<?> values)) {
            return List.of();
        }

        List<Long> result = new ArrayList<>();

        for (Object value : values) {
            if (value instanceof Number number) {
                long id = number.longValue();

                if (id > 0) {
                    result.add(id);
                }
            }
        }

        return List.copyOf(result);
    }

    @Transactional
    public Optional<Long> getProductIdByPosition(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId,
            int position
    ) {
        List<Long> ids = getLastPresentedProductIds(
                conversationId,
                principal,
                topicId
        );

        if (position < 1 || position > ids.size()) {
            return Optional.empty();
        }

        return Optional.of(ids.get(position - 1));
    }

    @Transactional
    public void updateSummary(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId,
            String summary
    ) {
        ChatbotTopic topic = getAuthorizedTopic(
                conversationId,
                principal,
                topicId
        );

        if (summary != null && summary.length() > 5000) {
            throw new IllegalArgumentException(
                    "Context summary quá dài"
            );
        }

        topic.setContextSummary(summary);
    }

    private ChatbotTopic getAuthorizedTopic(
            String conversationId,
            ConversationPrincipal principal,
            Long topicId
    ) {
        sessionService.getActiveConversation(
                conversationId,
                principal
        );

        if (topicId == null) {
            throw new IllegalArgumentException(
                    "Topic ID không được null"
            );
        }

        return topicRepository
                .findByIdAndConversationId(
                        topicId,
                        conversationId
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Topic không thuộc conversation"
                        )
                );
    }

    private Map<String, Object> copyContext(
            Map<String, Object> source
    ) {
        if (source == null) {
            return new HashMap<>();
        }

        Map<String, Object> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key,
                value instanceof List<?> list ? new ArrayList<>(list) : value));
        return copy;
    }

    private void validateField(String field) {
        if (field == null || !ALLOWED_FIELDS.contains(field)) {
            throw new IllegalArgumentException(
                    "Context field không hợp lệ: " + field
            );
        }
    }

    private void validateValue(String field, Object value) {

        if (LIST_FIELDS.contains(field)) {
            if (!(value instanceof List<?> values)
                    || values.size() > 20) {
                throw new IllegalArgumentException(
                        "Danh sách context không hợp lệ: " + field
                );
            }

            for (Object item : values) {
                if (!(item instanceof String text)
                        || text.isBlank()
                        || text.length() > 200) {
                    throw new IllegalArgumentException(
                            "Phần tử context không hợp lệ: " + field
                    );
                }
            }

            return;
        }

        if (NUMERIC_FIELDS.contains(field)) {
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException(
                        "Giá trị số không hợp lệ: " + field
                );
            }

            double numericValue = number.doubleValue();

            if (!Double.isFinite(numericValue)
                    || numericValue < 0) {
                throw new IllegalArgumentException(
                        "Giá trị số không hợp lệ: " + field
                );
            }

            if (Set.of("minBattery", "minScreenSize").contains(field)
                    && numericValue <= 0) {
                throw new IllegalArgumentException(
                        "Giá trị phải lớn hơn 0: " + field
                );
            }

            return;
        }

        if (!(value instanceof String text)
                || text.length() > 200) {
            throw new IllegalArgumentException(
                    "Giá trị context không hợp lệ: " + field
            );
        }
    }

    private void validatePriceRange(
            Map<String, Object> context
    ) {
        Object min = context.get("minPrice");
        Object max = context.get("maxPrice");

        if (min instanceof Number minNumber
                && max instanceof Number maxNumber) {

            BigDecimal minPrice =
                    new BigDecimal(minNumber.toString());

            BigDecimal maxPrice =
                    new BigDecimal(maxNumber.toString());

            if (minPrice.compareTo(maxPrice) > 0) {
                throw new IllegalArgumentException(
                        "Giá tối thiểu không được lớn hơn giá tối đa"
                );
            }
        }
    }
}
