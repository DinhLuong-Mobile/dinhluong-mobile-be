
package com.dinhluong.dlmstore.chatbot.dto;

import java.util.List;
import java.util.Map;

public record ConversationContextUpdate(
        Map<String, Object> setFields,
        List<String> clearFields,
        List<Long> presentedProductIds
) {
}
